package com.pos.suppliers.dto;

import com.pos.suppliers.domain.Supplier;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SupplierResponse(
        UUID id,
        String supplierCode,
        String name,
        String phone,
        String email,
        String address,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static SupplierResponse fromEntity(Supplier supplier) {
        return new SupplierResponse(
                supplier.getId(),
                supplier.getSupplierCode(),
                supplier.getName(),
                supplier.getPhone(),
                supplier.getEmail(),
                supplier.getAddress(),
                supplier.isActive(),
                supplier.getCreatedAt(),
                supplier.getUpdatedAt()
        );
    }
}
