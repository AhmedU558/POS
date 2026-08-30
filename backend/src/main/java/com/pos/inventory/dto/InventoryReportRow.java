package com.pos.inventory.dto;

import com.pos.inventory.domain.InventoryBalance;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InventoryReportRow(
        UUID productId,
        String productName,
        String sku,
        UUID storeId,
        String storeName,
        BigDecimal quantity,
        BigDecimal minStock,
        boolean belowMinimum,
        Instant lastUpdatedAt
) {
    public static InventoryReportRow fromEntity(InventoryBalance balance) {
        BigDecimal minStock = balance.getProduct().getMinStock();
        BigDecimal quantity = balance.getQuantity();
        boolean below = minStock != null && quantity.compareTo(minStock) <= 0;
        return new InventoryReportRow(
                balance.getProduct().getId(),
                balance.getProduct().getName(),
                balance.getProduct().getSku(),
                balance.getStore().getId(),
                balance.getStore().getName(),
                quantity,
                minStock,
                below,
                balance.getLastUpdatedAt()
        );
    }
}
