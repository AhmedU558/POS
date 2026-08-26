package com.pos.auth.service;

import com.pos.audit.domain.AuditActor;
import com.pos.audit.domain.AuditEvent;
import com.pos.audit.domain.AuditRequestContext;
import com.pos.audit.service.AuditRecorder;
import com.pos.auth.dto.LoginResponse;
import com.pos.auth.security.AuthenticationRateLimiter;
import com.pos.auth.security.JwtTokenProvider;
import com.pos.common.exception.ApiException;
import com.pos.common.response.ErrorCode;
import com.pos.users.domain.User;
import com.pos.users.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    private static final String AUDITED_ENTITY = "User";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationRateLimiter rateLimiter;
    private final AuditRecorder auditRecorder;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       AuthenticationRateLimiter rateLimiter,
                       AuditRecorder auditRecorder,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.rateLimiter = rateLimiter;
        this.auditRecorder = auditRecorder;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public LoginResponse login(String username, String password, String ip, AuditRequestContext requestContext) {
        rateLimiter.checkAndRecordLoginAttempt(ip, username);

        User user = userRepository.findByUsername(username).orElse(null);

        if (user == null) {
            rateLimiter.recordFailedAttempt(ip, username);
            log.warn("Login failed: user '{}' not found", username);
            throw rejectCredentials();
        }

        if (!user.isActive()) {
            rateLimiter.recordFailedAttempt(ip, username);
            log.warn("Login failed: user '{}' is deactivated", username);
            throw rejectCredentials();
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            rateLimiter.recordFailedAttempt(ip, username);
            
            auditRecorder.record(new AuditEvent(
                    AuditActor.user(user.getId()),
                    LOGIN_FAILED, AUDITED_ENTITY, user.getId(), null, null, requestContext));
                    
            log.warn("Login failed: invalid password for '{}'", username);
            throw rejectCredentials();
        }

        rateLimiter.recordSuccessfulAttempt(ip, username);
        
        auditRecorder.record(new AuditEvent(
                AuditActor.user(user.getId()),
                LOGIN_SUCCESS, AUDITED_ENTITY, user.getId(), null, null, requestContext));

        String token = jwtTokenProvider.generateToken(user.getUsername());
        String refreshToken = refreshTokenService.createRefreshToken(user.getUsername());
        
        return new LoginResponse(token, refreshToken, user.isPasswordChangeRequired());
    }

    @Transactional(readOnly = true)
    public com.pos.users.dto.UserResponse getCurrentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTHENTICATION_REQUIRED, "User not found"));

        if (!user.isActive()) {
            throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED, "User is deactivated");
        }

        return com.pos.users.dto.UserResponse.from(user);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public LoginResponse refresh(String refreshToken, AuditRequestContext requestContext) {
        RefreshTokenService.RotationResult result = refreshTokenService.rotateRefreshToken(refreshToken);
        
        User user = userRepository.findByUsername(result.username()).orElse(null);
        if (user == null || !user.isActive()) {
            refreshTokenService.revokeFamily(refreshTokenService.getFamilyIdFromToken(refreshToken));
            log.warn("Refresh failed: user '{}' is missing or deactivated", result.username());
            throw rejectCredentials();
        }

        if (user.getCredentialsChangedAt() != null) {
            java.time.Instant credentialsChangedAt = user.getCredentialsChangedAt().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
            if (result.iat().isBefore(credentialsChangedAt)) {
                refreshTokenService.revokeFamily(refreshTokenService.getFamilyIdFromToken(refreshToken));
                log.warn("Refresh failed: user '{}' credentials changed after token issued", result.username());
                throw rejectCredentials();
            }
        }

        auditRecorder.record(new AuditEvent(
                AuditActor.user(user.getId()),
                "REFRESH_SUCCESS", AUDITED_ENTITY, user.getId(), null, null, requestContext));

        String newAccessToken = jwtTokenProvider.generateToken(user.getUsername());
        return new LoginResponse(newAccessToken, result.newRefreshToken(), user.isPasswordChangeRequired());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void logout(String refreshToken, AuditRequestContext requestContext) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return; // Nothing to revoke
        }

        String familyId = refreshTokenService.getFamilyIdFromToken(refreshToken);
        if (familyId != null) {
            refreshTokenService.revokeFamily(familyId);
            
            // Only try to audit if the token was at least well-formed enough to parse
            try {
                String username = jwtTokenProvider.getRefreshClaims(refreshToken).getSubject();
                userRepository.findByUsername(username).ifPresent(user -> {
                    auditRecorder.record(new AuditEvent(
                            AuditActor.user(user.getId()),
                            "LOGOUT_SUCCESS", AUDITED_ENTITY, user.getId(), null, null, requestContext));
                });
            } catch (Exception e) {
                // Ignore audit if token is expired/invalid during logout
            }
        }
    }

    private static ApiException rejectCredentials() {
        return new ApiException(ErrorCode.AUTHENTICATION_REQUIRED, "Invalid credentials");
    }
}
