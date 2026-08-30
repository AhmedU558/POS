package com.pos.purchases.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseOrderItemRequest(
        @NotNull UUID productId,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity
) {
}
