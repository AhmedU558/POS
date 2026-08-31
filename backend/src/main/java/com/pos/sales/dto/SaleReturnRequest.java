package com.pos.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record SaleReturnRequest(
        @NotNull UUID storeId,
        @NotNull UUID terminalId,
        @NotNull UUID registerId,
        @NotNull UUID registerSessionId,
        @NotEmpty @Valid List<SaleReturnItemRequest> items,
        @NotEmpty @Valid List<SalePaymentRequest> refundPayments,
        String reason
) {
}
