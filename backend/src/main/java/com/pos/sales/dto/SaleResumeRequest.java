package com.pos.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record SaleResumeRequest(
        @NotNull UUID registerSessionId,
        @NotNull @Size(min = 1) @Valid List<SalePaymentRequest> payments
) {
}
