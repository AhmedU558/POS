package com.pos.finance.dto;

import java.math.BigDecimal;

public record InventoryReportResponse(
        String productName,
        String sku,
        BigDecimal currentQuantity,
        BigDecimal value
) {}
