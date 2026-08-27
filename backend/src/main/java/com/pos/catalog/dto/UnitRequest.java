package com.pos.catalog.dto;

import jakarta.validation.constraints.NotBlank;

public record UnitRequest(
        @NotBlank(message = "Code is required") String code,
        @NotBlank(message = "Name is required") String name,
        Boolean isActive
) {}
