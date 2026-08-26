package com.pos.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pos.common.config.RequestCorrelation;
import com.pos.common.response.ApiErrorResponse;
import com.pos.common.response.ErrorCode;
import com.pos.users.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Confines an account that must rotate its password to the operations that let it do so.
 *
 * <p>Runs after authentication and before the authorization decision, which is where ADR-013 places
 * it: rotation is not an authentication outcome. Failing the login itself would strand the holder
 * with no route to fix their own account, so the session is established and then constrained.
 *
 * <p>The flag is read from the database on every request, never from a token claim or anything the
 * client sent. That is the whole point of ADR-013's choice: marking an account takes effect on its
 * next request rather than whenever its token happens to expire, and no client can assert its way
 * out of the requirement.
 */
@Component
public class PasswordRotationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PasswordRotationFilter.class);

    /**
     * The only routes reachable while rotation is outstanding (AMD-002 §3).
     *
     * <p>Change-password is the operation that clears the requirement; logout lets a blocked
     * session end cleanly; {@code /auth/me} lets a client render who is signed in. Nothing else
     * belongs here — a wider list would leave the credential usable for real work.
     */
    private static final Set<String> ALLOW_LIST =
            Set.of(
                    "/api/v1/auth/change-password",
                    "/api/v1/auth/logout",
                    "/api/v1/auth/me");

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public PasswordRotationFilter(UserRepository userRepository, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (isAllowListed(request) || !isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        boolean rotationPending;
        try {
            rotationPending = isRotationPending(username);
        } catch (RuntimeException ex) {
            // This filter sits after ExceptionTranslationFilter, so nothing downstream will turn
            // an exception here into the standard envelope: it would escape as the container's
            // default error page. A database blip would therefore answer every authenticated
            // request with a non-conforming body.
            //
            // Fail closed. If the rotation state cannot be read, the account cannot be confirmed
            // fit to proceed.
            log.error("Could not read rotation state for '{}'; failing closed", username, ex);
            reject(response, ErrorCode.INTERNAL_ERROR);
            return;
        }

        if (!rotationPending) {
            filterChain.doFilter(request, response);
            return;
        }

        reject(response, ErrorCode.PASSWORD_CHANGE_REQUIRED);
    }

    private boolean isRotationPending(String username) {
        return
                userRepository
                        .findByUsername(username)
                        .map(user -> user.isPasswordChangeRequired())
                        // Fail CLOSED. An authenticated principal that resolves to no row is not
                        // a normal state, and the safe reading of it is "cannot confirm this
                        // account may proceed", not "let it through".
                        //
                        // This matters far more than it looks. Story 1.4 chooses what goes in the
                        // token subject; if it ever carries an email, a user id, or a differently
                        // cased username, every lookup here misses. Failing open would silently
                        // disable rotation enforcement system-wide while every test still passed
                        // and every request returned 200. Failing closed turns that same mistake
                        // into an immediate, loud, impossible-to-miss breakage.
                        .orElse(true);
    }

    /**
     * Exact match on the request path.
     *
     * <p>Not a prefix match: {@code startsWith("/api/v1/auth/")} would open every future
     * authentication endpoint, including ones added long after anyone remembers this filter exists.
     */
    private boolean isAllowListed(HttpServletRequest request) {
        return ALLOW_LIST.contains(pathWithinApplication(request));
    }

    /**
     * The request path with any servlet context path removed.
     *
     * <p>{@code getRequestURI()} includes the context path, but the entries above — and the
     * matchers in {@code SecurityConfig} — are written relative to the application. Deploying
     * under a context path such as {@code /pos} would therefore stop the allow-list matching while
     * the rest of the security configuration kept working, and a flagged account would be locked
     * out of the one endpoint that clears its own flag.
     */
    private static String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            String remainder = uri.substring(contextPath.length());
            return remainder.isEmpty() ? "/" : remainder;
        }
        return uri;
    }

    private boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    /**
     * Distinct from a plain {@code ACCESS_DENIED} on purpose: the client has to be able to tell
     * "you must rotate" from "you lack permission" in order to send the user somewhere useful
     * (UI/UX §28). Enforcement does not depend on the client doing so.
     */
    private void reject(HttpServletResponse response, ErrorCode code) throws IOException {
        ApiErrorResponse body =
                ApiErrorResponse.of(
                        code, code.defaultMessage(), RequestCorrelation.currentId());

        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
