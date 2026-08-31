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
        String storeAddress,
        String storeContact,
        String terminalName,
        String cashierName,
        String customerName,
        String status,
        BigDecimal subtotal,
        BigDecimal discountTotal,
        BigDecimal taxTotal,
        BigDecimal grandTotal,
        BigDecimal tenderedAmount,
        BigDecimal changeAmount,
        String fbrStatus,
        String fbrStatusLabel,
        String fbrInvoiceNumber,
        String fbrQrCode,
        List<SaleItemResponse> items,
        List<SalePaymentResponse> payments
) {
    public static SaleReceiptResponse fromEntity(Sale sale) {
        SaleResponse saleResponse = SaleResponse.fromEntity(sale);
        String cashierName = sale.getCashier() == null
                ? null
                : (sale.getCashier().getFirstName() + " " + sale.getCashier().getLastName()).trim();
        
        BigDecimal tenderedAmount = sale.getPayments().stream()
                .map(p -> p.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
                
        BigDecimal changeAmount = tenderedAmount.subtract(sale.getGrandTotal());
        if (changeAmount.compareTo(BigDecimal.ZERO) < 0) {
            changeAmount = BigDecimal.ZERO;
        }
        
        String fbrStatusLabel = switch (sale.getFbrStatus()) {
            case "NOT_CONFIGURED" -> "Not configured";
            case "SUBMISSION_SUCCESS" -> "Submission successful";
            case "SUBMISSION_FAILED" -> "Submission failed";
            case "RETRY_REQUIRED" -> "Retry required";
            default -> sale.getFbrStatus();
        };

        return new SaleReceiptResponse(
                saleResponse.id(),
                saleResponse.receiptNumber(),
                sale.getCreatedAt(),
                sale.getStore().getName(),
                null, // storeAddress not in domain yet
                null, // storeContact not in domain yet
                sale.getTerminal() == null ? null : sale.getTerminal().getName(),
                cashierName,
                sale.getCustomer() == null ? null : sale.getCustomer().getName(),
                saleResponse.status(),
                saleResponse.subtotal(),
                saleResponse.discountTotal(),
                saleResponse.taxTotal(),
                saleResponse.grandTotal(),
                tenderedAmount,
                changeAmount,
                sale.getFbrStatus(),
                fbrStatusLabel,
                sale.getFbrInvoiceNumber(),
                sale.getFbrQrCode(),
                saleResponse.items(),
                saleResponse.payments());
    }
}
