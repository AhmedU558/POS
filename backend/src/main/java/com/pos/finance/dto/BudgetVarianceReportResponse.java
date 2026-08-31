package com.pos.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BudgetVarianceReportResponse(
        UUID budgetId,
        String budgetName,
        LocalDate periodStart,
        LocalDate periodEnd,
        String category,
        BigDecimal allocatedAmount,
        BigDecimal actualAmount,
        BigDecimal varianceAmount
) {}
