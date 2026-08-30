package com.pos.customers.dto;

import com.pos.customers.domain.CreditTransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CustomerCreditTransactionRequest(
        @NotNull CreditTransactionType transactionType,
        @NotNull BigDecimal amount,
        @Size(min = 3, max = 3) String currencyCode,
        String referenceType,
        UUID referenceId
) {
}
