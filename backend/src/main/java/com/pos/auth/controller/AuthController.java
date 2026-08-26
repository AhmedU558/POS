package com.pos.auth.controller;

import com.pos.audit.domain.AuditRequestContext;
import com.pos.auth.dto.ChangePasswordRequest;
import com.pos.auth.dto.LoginRequest;
import com.pos.auth.dto.LoginResponse;
import com.pos.auth.service.PasswordChangeService;
import com.pos.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints.
 *
 * <p>Only the self-service password change exists so far; login, refresh, logout and
 * {@code /auth/me} arrive with Stories 1.4 to 1.6.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Credential management")
public class AuthController {

    private final PasswordChangeService passwordChangeService;
    private final AuthService authService;

    public AuthController(PasswordChangeService passwordChangeService, AuthService authService) {
        this.passwordChangeService = passwordChangeService;
        this.authService = authService;
    }

    /**
     * Changes the caller's own password.
     *
     * <p>Returns no body by design (AMD-002 §2): sending back the user or a token would invite the
     * response to be treated as an authentication result. A client needing fresh state re-reads
     * {@code /auth/me}.
     *
     * <p>The subject comes from the authenticated principal, never from the request, so a caller
     * cannot change somebody else's password by naming them.
     */
    @PostMapping("/change-password")
    @Operation(
            summary = "Change your own password",
            description =
                    "Replaces the caller's own password and clears any outstanding rotation"
                            + " requirement. The current password is re-verified even though the"
                            + " caller is authenticated.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Password changed. No body.",
                content = @Content),
        @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR - a field is missing"
                + " or malformed.", content = @Content),
        @ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED - no valid"
                + " credentials, or the current password is incorrect. Deliberately the same"
                + " response in both cases.", content = @Content),
        @ApiResponse(responseCode = "422", description = "BUSINESS_RULE_VIOLATION - the new"
                + " password fails policy or matches the current one.", content = @Content)
    })
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        passwordChangeService.changePassword(
                authentication.getName(),
                request.currentPassword(),
                request.newPassword(),
                AuditRequestContext.of(
                        httpRequest.getRemoteAddr(), httpRequest.getHeader(HttpHeaders.USER_AGENT)));

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    @Operation(
            summary = "Authenticate user",
            description = "Authenticates a user and returns a JWT access token.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully authenticated",
                content = @Content),
        @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR - malformed request",
                content = @Content),
        @ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED - invalid credentials",
                content = @Content),
        @ApiResponse(responseCode = "429", description = "RATE_LIMITED - too many attempts",
                content = @Content)
    })
    public ResponseEntity<com.pos.common.response.ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        LoginResponse response = authService.login(
                request.username(),
                request.password(),
                httpRequest.getRemoteAddr(),
                AuditRequestContext.of(
                        httpRequest.getRemoteAddr(), httpRequest.getHeader(HttpHeaders.USER_AGENT)));

        return ResponseEntity.ok(com.pos.common.response.ApiResponse.of(response, com.pos.common.config.RequestCorrelation.currentId()));
    }

    @org.springframework.web.bind.annotation.GetMapping("/me")
    @Operation(
            summary = "Get current user",
            description = "Returns the currently authenticated user's profile and permissions.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User profile returned", content = @Content),
        @ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED - no valid credentials", content = @Content)
    })
    public ResponseEntity<com.pos.common.response.ApiResponse<com.pos.users.dto.UserResponse>> getCurrentUser(
            Authentication authentication) {

        com.pos.users.dto.UserResponse response = authService.getCurrentUser(authentication.getName());
        return ResponseEntity.ok(com.pos.common.response.ApiResponse.of(response, com.pos.common.config.RequestCorrelation.currentId()));
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Refresh token",
            description = "Issues a new access token and rotates the refresh token.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully refreshed", content = @Content),
        @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR - malformed request", content = @Content),
        @ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED - invalid or revoked refresh token", content = @Content)
    })
    public ResponseEntity<com.pos.common.response.ApiResponse<LoginResponse>> refresh(
            @Valid @RequestBody com.pos.auth.dto.RefreshTokenRequest request,
            HttpServletRequest httpRequest) {

        LoginResponse response = authService.refresh(
                request.refreshToken(),
                AuditRequestContext.of(
                        httpRequest.getRemoteAddr(), httpRequest.getHeader(HttpHeaders.USER_AGENT)));

        return ResponseEntity.ok(com.pos.common.response.ApiResponse.of(response, com.pos.common.config.RequestCorrelation.currentId()));
    }

    @PostMapping("/logout")
    @Operation(
            summary = "Logout",
            description = "Invalidates the current session/refresh token.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Logged out. No body.", content = @Content)
    })
    public ResponseEntity<Void> logout(
            @RequestBody(required = false) com.pos.auth.dto.RefreshTokenRequest request,
            HttpServletRequest httpRequest) {

        String refreshToken = request != null ? request.refreshToken() : null;
        authService.logout(
                refreshToken,
                AuditRequestContext.of(
                        httpRequest.getRemoteAddr(), httpRequest.getHeader(HttpHeaders.USER_AGENT)));

        return ResponseEntity.noContent().build();
    }
}
