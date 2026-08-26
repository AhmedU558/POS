package com.pos.auth.service;

import com.pos.audit.domain.AuditActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.pos.audit.domain.AuditEvent;
import com.pos.audit.domain.AuditRequestContext;
import com.pos.audit.service.AuditRecorder;
import com.pos.common.exception.ApiException;
import com.pos.common.response.ErrorCode;
import com.pos.users.domain.User;
import com.pos.users.repository.UserRepository;
import com.pos.auth.security.AuthenticationRateLimiter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Replaces a user's own password.
 *
 * <p>One transaction: the new hash, the cleared rotation flag and the audit record commit together
 * or not at all. A partial success would either strand an account permanently in "must change
 * password" or clear the requirement without a new credential behind it.
 */
@Service
public class PasswordChangeService {

    private static final Logger log = LoggerFactory.getLogger(PasswordChangeService.class);

    /** Approved policy (AMD-002): length only, no composition rules. */
    public static final int MINIMUM_PASSWORD_LENGTH = 12;

    /**
     * BCrypt hashes at most 72 bytes and silently discards the rest.
     *
     * <p>Not a composition rule and not an arbitrary ceiling — it is the algorithm's own bound.
     * Accepting longer input would mean a 200-character passphrase is protected by its first 72
     * bytes while appearing stronger, and would make the "must differ from the current one" check
     * below compare truncated forms, rejecting a genuinely different password as identical.
     * Rejecting is honest; truncating is not.
     */
    public static final int MAXIMUM_PASSWORD_BYTES = 72;

    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";

    private static final String AUDITED_ENTITY = "User";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditRecorder auditRecorder;
    private final AuthenticationRateLimiter rateLimiter;

    public PasswordChangeService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuditRecorder auditRecorder,
            AuthenticationRateLimiter rateLimiter) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditRecorder = auditRecorder;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Verifies the current password, applies the policy, then changes the credential.
     *
     * <p>The order is deliberate. The current password is checked first, so a caller who cannot
     * prove they hold the account learns nothing about the password policy — and gets the same
     * answer whether the account exists or not.
     */
    @Transactional
    public void changePassword(
            String username,
            String currentPassword,
            String newPassword,
            AuditRequestContext requestContext) {
        
        rateLimiter.checkAndRecordLoginAttempt(requestContext.ipAddress(), username);

        User user =
                userRepository
                        .findByUsername(username)
                        // Same response as a wrong password: an authenticated principal naming an
                        // account that no longer exists must not be able to probe for it.
                        .orElseThrow(PasswordChangeService::rejectCredentials);

        if (!user.isActive()) {
            // A deactivated account holding a still-valid token must not be able to set a fresh
            // credential and clear its own rotation flag. Reported as a credential failure rather
            // than a distinct code, so the endpoint does not disclose account status either.
            log.warn("Rejected password change for '{}': account is not active", username);
            rateLimiter.recordFailedAttempt(requestContext.ipAddress(), username);
            throw rejectCredentials();
        }

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            // Authentication proves the session; it does not prove the person. A stolen access
            // token must not be enough to seize the account permanently.
            log.warn("Rejected password change for '{}': current password did not match", username);
            rateLimiter.recordFailedAttempt(requestContext.ipAddress(), username);
            throw rejectCredentials();
        }

        // Code points, not String.length(). Six emoji are twelve UTF-16 code units, so a
        // length() check would accept a six-character password as satisfying a twelve-character
        // policy.
        if (newPassword.codePointCount(0, newPassword.length()) < MINIMUM_PASSWORD_LENGTH) {
            throw new ApiException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "The new password must be at least "
                            + MINIMUM_PASSWORD_LENGTH
                            + " characters.");
        }
        if (newPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > MAXIMUM_PASSWORD_BYTES) {
            throw new ApiException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "The new password must be at most "
                            + MAXIMUM_PASSWORD_BYTES
                            + " bytes.");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            // Otherwise the rotation requirement could be satisfied by re-setting the very
            // credential it exists to retire.
            throw new ApiException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "The new password must differ from the current one.");
        }

        // One operation: the hash cannot be replaced without the flag clearing, and the flag
        // cannot clear without a new hash being set.
        user.changePassword(passwordEncoder.encode(newPassword));
        userRepository.saveAndFlush(user);
        
        rateLimiter.recordSuccessfulAttempt(requestContext.ipAddress(), username);

        // Carries the caller's address and user agent. Database Design section 20.1 records both
        // "when available", and on a user-driven HTTP action they are: this is the first audited
        // action in the system where a human is actually at the other end.
        auditRecorder.record(
                new AuditEvent(
                        AuditActor.user(user.getId()),
                        PASSWORD_CHANGED,
                        AUDITED_ENTITY,
                        user.getId(),
                        null,
                        null,
                        requestContext));
    }

    /**
     * One response for every credential failure.
     *
     * <p>AMD-002 deliberately reuses {@code AUTHENTICATION_REQUIRED} rather than defining a
     * distinct code, so the endpoint cannot be turned into a password oracle by a caller holding a
     * hijacked session.
     */
    private static ApiException rejectCredentials() {
        return new ApiException(
                ErrorCode.AUTHENTICATION_REQUIRED, "The current password is not correct.");
    }
}
