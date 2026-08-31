package com.pos.sales.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record SaleReturnItemRequest(
        @NotNull UUID saleItemId,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal returnQuantity
) {
}
