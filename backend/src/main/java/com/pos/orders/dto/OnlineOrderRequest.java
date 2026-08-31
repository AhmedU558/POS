package com.pos.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OnlineOrderRequest(
        @NotNull UUID storeId,
        UUID customerId,
        @NotBlank String channel,
        @NotBlank String externalOrderId,
        @NotNull BigDecimal subtotal,
        @NotNull BigDecimal discountTotal,
        @NotNull BigDecimal taxTotal,
        @NotNull BigDecimal grandTotal,
        @NotBlank String currencyCode,
        String notes,
        @NotEmpty @Valid List<OnlineOrderItemRequest> items
) {}
