package com.pos.suppliers.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SupplierUpdateRequest(
        @NotBlank String supplierCode,
        @NotBlank String name,
        String phone,
        String email,
        String address,
        @NotNull Boolean isActive
) {
}
