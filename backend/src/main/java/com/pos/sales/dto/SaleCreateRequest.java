package com.pos.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record SaleCreateRequest(
        @NotNull UUID storeId,
        @NotNull UUID terminalId,
        @NotNull UUID registerId,
        @NotNull UUID registerSessionId,
        UUID customerId,
        @NotEmpty @Valid List<SaleItemRequest> items,
        @Valid List<SalePaymentRequest> payments
) {
}
