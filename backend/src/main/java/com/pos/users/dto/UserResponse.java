package com.pos.users.dto;

import com.pos.users.domain.User;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String email,
        String firstName,
        String lastName,
        boolean active,
        boolean passwordChangeRequired,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt,
        Set<String> permissions
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.isActive(),
                user.isPasswordChangeRequired(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.permissionCodes()
        );
    }
}