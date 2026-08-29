package com.pos.catalog.dto;

import com.pos.catalog.entity.Product;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductResponse(
    UUID id,
    String sku,
    String name,
    String description,
    UUID categoryId,
    UUID brandId,
    UUID unitId,
    BigDecimal purchasePrice,
    BigDecimal sellingPrice,
    BigDecimal wholesalePrice,
    BigDecimal taxRate,
    BigDecimal minStock,
    BigDecimal maxStock,
    boolean trackBatch,
    boolean trackExpiry,
    boolean isActive,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static ProductResponse fromEntity(Product p) {
        return new ProductResponse(
            p.getId(), p.getSku(), p.getName(), p.getDescription(),
            p.getCategory() != null ? p.getCategory().getId() : null,
            p.getBrand() != null ? p.getBrand().getId() : null,
            p.getUnit() != null ? p.getUnit().getId() : null,
            p.getPurchasePrice(), p.getSellingPrice(), p.getWholesalePrice(),
            p.getTaxRate(), p.getMinStock(), p.getMaxStock(),
            p.isTrackBatch(), p.isTrackExpiry(), p.isActive(),
            p.getCreatedAt(), p.getUpdatedAt()
        );
    }
}
