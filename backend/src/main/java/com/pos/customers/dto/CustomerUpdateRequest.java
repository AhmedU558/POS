package com.pos.customers.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CustomerUpdateRequest(
        @NotBlank String customerCode,
        @NotBlank String name,
        String phone,
        String email,
        String address,
        @NotNull @DecimalMin("0.0") BigDecimal creditLimit,
        @NotNull Boolean isActive
) {
}
