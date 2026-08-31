package com.pos.quotations.dto;

import com.pos.quotations.domain.Quotation;
import com.pos.sales.dto.SaleItemResponse;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record QuotationResponse(
        UUID id,
        String quotationNumber,
        String status,
        BigDecimal subtotal,
        BigDecimal discountTotal,
        BigDecimal taxTotal,
        BigDecimal grandTotal,
        OffsetDateTime expirationDate,
        String notes,
        List<SaleItemResponse> items
) {
    public static QuotationResponse fromEntity(Quotation quotation) {
        return new QuotationResponse(
                quotation.getId(),
                quotation.getQuotationNumber(),
                quotation.getStatus(),
                quotation.getSubtotal(),
                quotation.getDiscountTotal(),
                quotation.getTaxTotal(),
                quotation.getGrandTotal(),
                quotation.getExpirationDate(),
                quotation.getNotes(),
                quotation.getItems().stream()
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
