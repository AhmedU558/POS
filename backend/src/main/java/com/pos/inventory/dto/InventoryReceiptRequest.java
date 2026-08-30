package com.pos.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record InventoryReceiptRequest(
        @NotNull(message = "Store ID is required") UUID storeId,
        @NotNull(message = "Product ID is required") UUID productId,
        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be greater than zero")
        BigDecimal quantity
) {
}
