package com.pos.sales.dto;

import com.pos.sales.domain.Sale;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SaleReceiptResponse(
        UUID saleId,
        String receiptNumber,
        OffsetDateTime createdAt,
        String storeName,
        String cashierName,
        String customerName,
        String status,
        BigDecimal subtotal,
        BigDecimal discountTotal,
        BigDecimal taxTotal,
        BigDecimal grandTotal,
        List<SaleItemResponse> items,
        List<SalePaymentResponse> payments
) {
    public static SaleReceiptResponse fromEntity(Sale sale) {
        SaleResponse saleResponse = SaleResponse.fromEntity(sale);
        String cashierName = sale.getCashier() == null
                ? null
                : (sale.getCashier().getFirstName() + " " + sale.getCashier().getLastName()).trim();
        return new SaleReceiptResponse(
                saleResponse.id(),
                saleResponse.receiptNumber(),
                sale.getCreatedAt(),
                sale.getStore().getName(),
                cashierName,
                sale.getCustomer() == null ? null : sale.getCustomer().getName(),
                saleResponse.status(),
                saleResponse.subtotal(),
                saleResponse.discountTotal(),
                saleResponse.taxTotal(),
                saleResponse.grandTotal(),
                saleResponse.items(),
                saleResponse.payments());
    }
}
