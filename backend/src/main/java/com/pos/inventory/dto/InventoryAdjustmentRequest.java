package com.pos.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record InventoryAdjustmentRequest(
        @NotNull(message = "Store ID is required") UUID storeId,
        @NotNull(message = "Product ID is required") UUID productId,
        @NotNull(message = "Quantity difference is required") BigDecimal quantity,
        @NotBlank(message = "Reason is required for adjustments") String reason
) {
}
