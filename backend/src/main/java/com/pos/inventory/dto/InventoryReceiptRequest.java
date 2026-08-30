package com.pos.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InventoryReceiptRequest(
        @NotNull(message = "Store ID is required") UUID storeId,
        @NotNull(message = "Product ID is required") UUID productId,
        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be greater than zero")
        BigDecimal quantity,
        @Size(max = 100) String batchNumber,
        LocalDate expirationDate,
        LocalDate manufacturingDate
) {
    public InventoryReceiptRequest(UUID storeId, UUID productId, BigDecimal quantity) {
        this(storeId, productId, quantity, null, null, null);
    }
}
