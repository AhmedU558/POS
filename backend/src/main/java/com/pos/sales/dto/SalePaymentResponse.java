package com.pos.sales.dto;

import java.math.BigDecimal;

public record SalePaymentResponse(
        String paymentMethod,
        BigDecimal amount
) {
}
