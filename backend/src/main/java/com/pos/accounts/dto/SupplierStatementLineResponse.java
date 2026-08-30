package com.pos.accounts.dto;

import com.pos.accounts.domain.SupplierStatementLineType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SupplierStatementLineResponse(
        SupplierStatementLineType type,
        LocalDate date,
        UUID invoiceId,
        String invoiceNumber,
        UUID paymentId,
        BigDecimal debit,
        BigDecimal credit,
        BigDecimal runningBalance
) {
}
