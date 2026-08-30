package com.pos.suppliers.dto;

import com.pos.suppliers.domain.SupplierProduct;

import java.util.UUID;

public record SupplierProductResponse(
        UUID id,
        UUID productId,
        String sku,
        String name,
        boolean active
) {
    public static SupplierProductResponse fromEntity(SupplierProduct association) {
        return new SupplierProductResponse(
                association.getId(),
                association.getProduct().getId(),
                association.getProduct().getSku(),
                association.getProduct().getName(),
                association.getProduct().isActive()
        );
    }
}
