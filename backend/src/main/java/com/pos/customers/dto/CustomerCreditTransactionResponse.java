package com.pos.customers.dto;

import com.pos.customers.domain.CreditTransactionType;
import com.pos.customers.domain.CustomerCreditTransaction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CustomerCreditTransactionResponse(
        UUID id,
        CreditTransactionType transactionType,
        BigDecimal amount,
        String referenceType,
        UUID referenceId,
        BigDecimal balanceAfter,
        OffsetDateTime createdAt
) {
    public static CustomerCreditTransactionResponse fromEntity(CustomerCreditTransaction transaction) {
        return new CustomerCreditTransactionResponse(
                transaction.getId(),
                transaction.getTransactionType(),
                transaction.getAmount(),
                transaction.getReferenceType(),
                transaction.getReferenceId(),
                transaction.getBalanceAfter(),
                transaction.getCreatedAt()
        );
    }
}
