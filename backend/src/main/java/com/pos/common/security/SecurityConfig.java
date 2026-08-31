package com.pos.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.pos.auth.security.PasswordRotationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import com.pos.auth.security.JwtAuthenticationFilter;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Security foundation.
 *
 * <p>Implements the filter-chain shape described in System Architecture Document section 15 and
 * the API protection rules in REST API Specification section 29. Authentication mechanics (JWT
 * validation, refresh tokens, user details) are deliberately absent; they are Phase 1 scope per
 * Implementation Plan section 11.
 *
 * <p>{@code @EnableMethodSecurity} is switched on now so that the permission codes defined in REST
 * API Specification section 4.3 have an enforcement mechanism available the moment the first
 * protected operation is written.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** Correlation header, exposed to browsers so clients can report a request id on failures. */
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** Idempotency header required by REST API Specification section 6. */
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final List<String> allowedOrigins;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final PasswordRotationFilter passwordRotationFilter;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;

    public SecurityConfig(
            @Value("${app.security.cors.allowed-origins}") List<String> allowedOrigins,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            PasswordRotationFilter passwordRotationFilter,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RateLimitFilter rateLimitFilter) {
        this.allowedOrigins = allowedOrigins;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.passwordRotationFilter = passwordRotationFilter;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Stateless bearer-token API: no session cookie exists for an attacker to ride,
            // so CSRF protection is not applicable (SAD section 15).
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> auth
                // API contract health probe (returns the standard response envelope).
                .requestMatchers("/api/v1/health").permitAll()
                // Infrastructure liveness probe used by container health checks.
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                // Only the genuinely public authentication endpoints, enumerated rather than
                // wildcarded. REST API Specification section 4.2 marks login, forgot-password and
                // reset-password Public; logout, refresh and me require authentication, and so
                // does change-password (AMD-002). A blanket /api/v1/auth/** would have exposed
                // change-password to anyone, and would silently expose every endpoint added here
                // in future.
                .requestMatchers(
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/forgot-password",
                        "/api/v1/auth/reset-password")
                    .permitAll()
                // OpenAPI documentation. Disabled entirely in production through springdoc
                // configuration (API spec section 32).
                .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                // CORS preflight must not require credentials.
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .anyRequest().authenticated()
            );

        // The rate limit filter goes before everything else
        http.addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);

        // The JWT authentication filter is added here in Story 1.4 (Implementation Plan §11).
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        // After authentication, before the authorization decision: an account that must rotate its
        // password is confined to the operations that let it do so (ADR-013).
        http.addFilterBefore(passwordRotationFilter, AuthorizationFilter.class);

        return http.build();
    }

    /**
     * Suppresses Spring Boot's automatic servlet-level registration of {@link
     * PasswordRotationFilter}.
     *
     * <p>Boot registers every {@code Filter} bean with the servlet container. Without this, the
     * rotation check runs in two places at once: the position declared above, and again as a
     * plain servlet filter sitting <em>after</em> the whole security chain. {@code
     * OncePerRequestFilter} deduplicates, so the second registration is invisible in normal
     * operation — and that is the danger. It silently substitutes for the declared position, so
     * deleting the {@code addFilterBefore} line above would relocate enforcement rather than
     * remove it, with no symptom.
     *
     * <p>Disabling the registration makes the security chain the filter's only home, which is what
     * ADR-013 specifies. {@code PasswordRotationFilterWiringTests} pins both halves.
     */
    @Bean
    public FilterRegistrationBean<PasswordRotationFilter> passwordRotationFilterRegistration(
            PasswordRotationFilter filter) {
        FilterRegistrationBean<PasswordRotationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Browser access policy for the Next.js clients, which run on a different origin from the API
     * (UI/UX Specification section 4).
     *
     * <p>Origins come from configuration so that each environment declares its own, per SAD
     * section 2. Credentials are not allowed: the approved contract authenticates with an
     * {@code Authorization: Bearer} header, not cookies.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(
                List.of(
                        HttpMethod.GET.name(),
                        HttpMethod.POST.name(),
                        HttpMethod.PUT.name(),
                        HttpMethod.PATCH.name(),
                        HttpMethod.DELETE.name(),
                        HttpMethod.OPTIONS.name()));
        configuration.setAllowedHeaders(
                List.of(
                        HttpHeaders.AUTHORIZATION,
                        HttpHeaders.CONTENT_TYPE,
                        HttpHeaders.ACCEPT,
                        REQUEST_ID_HEADER,
                        IDEMPOTENCY_KEY_HEADER));
        configuration.setExposedHeaders(List.of(REQUEST_ID_HEADER));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    /** Adaptive password encoder required by System Architecture Document section 15. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
