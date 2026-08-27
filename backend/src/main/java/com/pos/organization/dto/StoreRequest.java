package com.pos.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StoreRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 3, min = 3) String currencyCode,
        @NotBlank @Size(max = 50) String timezone
) {}
