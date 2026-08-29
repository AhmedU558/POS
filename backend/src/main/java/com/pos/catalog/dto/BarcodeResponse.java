package com.pos.catalog.dto;

import com.pos.catalog.entity.ProductBarcode;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BarcodeResponse(
    UUID id,
    UUID productId,
    String barcode,
    boolean isPrimary,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static BarcodeResponse fromEntity(ProductBarcode pb) {
        return new BarcodeResponse(
            pb.getId(),
            pb.getProduct().getId(),
            pb.getBarcode(),
            pb.isPrimary(),
            pb.getCreatedAt(),
            pb.getUpdatedAt()
        );
    }
}
