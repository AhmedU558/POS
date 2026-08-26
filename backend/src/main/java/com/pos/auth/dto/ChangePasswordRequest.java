package com.pos.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/auth/change-password} (AMD-002).
 *
 * <p>Two fields, and only two. Anything else a client sends is discarded: the rotation state lives
 * in the database and is never taken from a request.
 *
 * <p>The minimum length is deliberately <em>not</em> a {@code @Size} constraint. AMD-002 maps a
 * policy failure to 422 {@code BUSINESS_RULE_VIOLATION} and a malformed request to 400
 * {@code VALIDATION_ERROR}; a bean-validation annotation would produce the wrong one.
 */
public record ChangePasswordRequest(
        @NotBlank(message = "currentPassword must not be blank") String currentPassword,
        @NotBlank(message = "newPassword must not be blank") String newPassword) {

    /** Never render the credentials. */
    @Override
    public String toString() {
        return "ChangePasswordRequest[redacted]";
    }
}
