package com.pos.sales.dto;

import com.pos.sales.domain.Sale;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record SaleResponse(
        UUID id,
        String receiptNumber,
        String status,
        BigDecimal subtotal,
        BigDecimal discountTotal,
        BigDecimal taxTotal,
        BigDecimal grandTotal,
        List<SalePaymentResponse> payments,
        List<SaleItemResponse> items
) {
    public static SaleResponse fromEntity(Sale sale) {
        return new SaleResponse(
                sale.getId(),
                sale.getReceiptNumber(),
                sale.getStatus(),
                sale.getSubtotal(),
                sale.getDiscountTotal(),
                sale.getTaxTotal(),
                sale.getGrandTotal(),
                sale.getPayments().stream()
                        .map(payment -> new SalePaymentResponse(
                                payment.getPaymentMethod().getCode(),
                                payment.getAmount()))
                        .toList(),
                sale.getItems().stream()
                        .map(item -> new SaleItemResponse(
                                item.getProduct().getId(),
                                item.getProduct().getSku(),
                                item.getProduct().getName(),
                                item.getQuantity(),
                                item.getUnitPrice(),
                                item.getDiscountAmount(),
                                item.getTaxAmount(),
                                item.getLineTotal()))
                        .toList());
    }
}
