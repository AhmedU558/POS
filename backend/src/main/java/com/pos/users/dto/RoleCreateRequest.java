package com.pos.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record RoleCreateRequest(
        @NotBlank @Size(max = 50) String name,
        @Size(max = 255) String description,
        @NotNull Set<UUID> permissionIds
) {}
