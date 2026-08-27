package com.pos.catalog.dto;

import jakarta.validation.constraints.NotBlank;

public record BrandRequest(
        @NotBlank(message = "Name is required") String name,
        String description,
        Boolean isActive
) {}
