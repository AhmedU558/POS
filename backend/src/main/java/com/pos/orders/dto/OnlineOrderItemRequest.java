package com.pos.orders.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record OnlineOrderItemRequest(
        @NotNull UUID productId,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
        @NotNull @DecimalMin(value = "0") BigDecimal unitPrice,
        @NotNull @DecimalMin(value = "0") BigDecimal discountAmount,
        @NotNull @DecimalMin(value = "0") BigDecimal taxAmount,
        @NotNull @DecimalMin(value = "0") BigDecimal lineTotal
) {}
