package com.pos.inventory.dto;

import com.pos.inventory.domain.InventoryBalance;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InventoryBalanceResponse(
        UUID productId,
        String productName,
        String sku,
        UUID storeId,
        String storeName,
        BigDecimal quantity,
        Instant lastUpdatedAt
) {
    public static InventoryBalanceResponse fromEntity(InventoryBalance balance) {
        return new InventoryBalanceResponse(
                balance.getProduct().getId(),
                balance.getProduct().getName(),
                balance.getProduct().getSku(),
                balance.getStore().getId(),
                balance.getStore().getName(),
                balance.getQuantity(),
                balance.getLastUpdatedAt()
        );
    }
}
