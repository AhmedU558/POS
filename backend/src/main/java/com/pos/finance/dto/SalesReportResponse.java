package com.pos.finance.dto;

import java.math.BigDecimal;

public record SalesReportResponse(
        String dimension,
        BigDecimal totalSales,
        BigDecimal totalDiscounts,
        BigDecimal totalTax,
        long orderCount
) {}
