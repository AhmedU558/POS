package com.pos.inventory.dto;

import com.pos.inventory.domain.InventoryBatch;
import com.pos.inventory.domain.StockAlert;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public record StockAlertResponse(
        UUID id,
        UUID storeId,
        String storeName,
        UUID productId,
        String productName,
        String sku,
        UUID batchId,
        String batchNumber,
        String alertType,
        BigDecimal quantity,
        BigDecimal minimumLevel,
        LocalDate expirationDate,
        String status,
        String suggestedAction,
        Integer daysRemaining,
        Instant createdAt,
        Instant acknowledgedAt
) {
    public static StockAlertResponse fromEntity(StockAlert alert, LocalDate today) {
        InventoryBatch batch = alert.getBatch();
        LocalDate expiration = alert.getExpirationDate();
        Integer daysRemaining = expiration == null || today == null
                ? null
                : (int) ChronoUnit.DAYS.between(today, expiration);
        return new StockAlertResponse(
                alert.getId(),
                alert.getStore().getId(),
                alert.getStore().getName(),
                alert.getProduct().getId(),
                alert.getProduct().getName(),
                alert.getProduct().getSku(),
                batch == null ? null : batch.getId(),
                batch == null ? null : batch.getBatchNumber(),
                alert.getAlertType(),
                alert.getQuantity(),
                alert.getMinimumLevel(),
                expiration,
                alert.getStatus(),
                suggestedAction(alert.getAlertType(), expiration, today),
                daysRemaining,
                alert.getCreatedAt(),
                alert.getAcknowledgedAt()
        );
    }

    static String suggestedAction(String alertType, LocalDate expiration, LocalDate today) {
        if (StockAlert.TYPE_LOW_STOCK.equals(alertType)) {
            return "Reorder";
        }
        if (expiration != null && today != null && expiration.isBefore(today)) {
            return "Review expired stock";
        }
        return "Review stock approaching expiry";
    }
}
