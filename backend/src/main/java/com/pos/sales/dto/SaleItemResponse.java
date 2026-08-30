package com.pos.sales.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SaleItemResponse(
        UUID productId,
        String sku,
        String name,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal lineTotal
) {
}
