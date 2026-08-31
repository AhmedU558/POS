package com.pos.sales.dto;

import com.pos.sales.domain.SaleReturn;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record SaleReturnResponse(
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
    public static SaleReturnResponse fromEntity(SaleReturn saleReturn) {
        return new SaleReturnResponse(
                saleReturn.getId(),
                saleReturn.getReceiptNumber(),
                saleReturn.getStatus(),
                saleReturn.getSubtotal(),
                saleReturn.getDiscountTotal(),
                saleReturn.getTaxTotal(),
                saleReturn.getGrandTotal(),
                saleReturn.getPayments().stream()
                        .map(payment -> new SalePaymentResponse(
                                payment.getPaymentMethod().getCode(),
                                payment.getAmount()))
                        .toList(),
                saleReturn.getItems().stream()
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
