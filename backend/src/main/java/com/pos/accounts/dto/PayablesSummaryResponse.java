package com.pos.accounts.dto;

import java.math.BigDecimal;

public record PayablesSummaryResponse(
        BigDecimal totalInvoiced,
        BigDecimal paid,
        BigDecimal outstanding,
        BigDecimal overdue
) {
}
