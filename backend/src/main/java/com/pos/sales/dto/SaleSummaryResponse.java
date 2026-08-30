package com.pos.sales.dto;

import com.pos.sales.domain.Sale;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SaleSummaryResponse(
        UUID id,
        String receiptNumber,
        String status,
        BigDecimal grandTotal,
        OffsetDateTime createdAt,
        String customerName,
        String cashierName
) {
    public static SaleSummaryResponse fromEntity(Sale sale) {
        String cashierName = sale.getCashier() == null
                ? null
                : (sale.getCashier().getFirstName() + " " + sale.getCashier().getLastName()).trim();
        return new SaleSummaryResponse(
                sale.getId(),
                sale.getReceiptNumber(),
                sale.getStatus(),
                sale.getGrandTotal(),
                sale.getCreatedAt(),
                sale.getCustomer() == null ? null : sale.getCustomer().getName(),
                cashierName);
    }
}
