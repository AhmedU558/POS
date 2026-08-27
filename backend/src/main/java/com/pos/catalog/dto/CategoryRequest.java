package com.pos.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record CategoryRequest(
        @NotBlank(message = "Name is required") String name,
        String description,
        UUID parentId,
        Boolean isActive
) {}
