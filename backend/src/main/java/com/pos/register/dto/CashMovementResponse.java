package com.pos.register.dto;

import com.pos.sales.domain.CashTransaction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CashMovementResponse(
        UUID id,
        UUID registerSessionId,
        String transactionType,
        BigDecimal amount,
        String reason,
        OffsetDateTime createdAt
) {
    public static CashMovementResponse fromEntity(CashTransaction transaction, UUID sessionId) {
        return new CashMovementResponse(
                transaction.getId(),
                sessionId,
                transaction.getTransactionType(),
                transaction.getAmount(),
                transaction.getReason(),
                transaction.getCreatedAt());
    }
}
