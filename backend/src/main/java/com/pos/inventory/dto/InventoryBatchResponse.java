package com.pos.inventory.dto;

import com.pos.inventory.domain.InventoryBatch;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public record InventoryBatchResponse(
        UUID id,
        UUID productId,
        String productName,
        String sku,
        UUID storeId,
        String storeName,
        String batchNumber,
        BigDecimal quantity,
        LocalDate expirationDate,
        LocalDate manufacturingDate,
        String status,
        Integer daysRemaining
) {
    public static InventoryBatchResponse fromEntity(InventoryBatch batch, LocalDate today, int windowDays) {
        LocalDate expiration = batch.getExpirationDate();
        Integer daysRemaining = expiration == null ? null : (int) ChronoUnit.DAYS.between(today, expiration);
        return new InventoryBatchResponse(
                batch.getId(),
                batch.getProduct().getId(),
                batch.getProduct().getName(),
                batch.getProduct().getSku(),
                batch.getStore().getId(),
                batch.getStore().getName(),
                batch.getBatchNumber(),
                batch.getQuantity(),
                expiration,
                batch.getManufacturingDate(),
                deriveStatus(expiration, today, windowDays),
                daysRemaining
        );
    }

    static String deriveStatus(LocalDate expiration, LocalDate today, int windowDays) {
        if (expiration == null) {
            return "OK";
        }
        if (expiration.isBefore(today)) {
            return "EXPIRED";
        }
        if (expiration.isEqual(today)) {
            return "EXPIRING_TODAY";
        }
        if (!expiration.isAfter(today.plusDays(windowDays))) {
            return "APPROACHING";
        }
        return "OK";
    }
}
