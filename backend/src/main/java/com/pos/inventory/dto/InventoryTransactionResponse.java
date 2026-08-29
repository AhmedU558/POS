package com.pos.inventory.dto;

import com.pos.inventory.domain.InventoryTransaction;
import com.pos.inventory.domain.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InventoryTransactionResponse(
        UUID id,
        UUID productId,
        String productName,
        UUID storeId,
        TransactionType transactionType,
        BigDecimal quantity,
        String reason,
        String createdByUsername,
        Instant createdAt
) {
    public static InventoryTransactionResponse fromEntity(InventoryTransaction tx) {
        String username = tx.getCreatedBy() != null ? tx.getCreatedBy().getUsername() : null;
        return new InventoryTransactionResponse(
                tx.getId(),
                tx.getProduct().getId(),
                tx.getProduct().getName(),
                tx.getStore().getId(),
                tx.getTransactionType(),
                tx.getQuantity(),
                tx.getReason(),
                username,
                tx.getCreatedAt()
        );
    }
}
