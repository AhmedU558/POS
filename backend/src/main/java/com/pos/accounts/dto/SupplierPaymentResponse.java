package com.pos.accounts.dto;

import com.pos.accounts.domain.SupplierPayment;
import com.pos.accounts.domain.SupplierPaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SupplierPaymentResponse(
        UUID id,
        UUID invoiceId,
        String invoiceNumber,
        BigDecimal amount,
        LocalDate paymentDate,
        SupplierPaymentMethod method,
        String reference,
        OffsetDateTime createdAt
) {
    public static SupplierPaymentResponse fromEntity(SupplierPayment payment) {
        return new SupplierPaymentResponse(
                payment.getId(),
                payment.getInvoice().getId(),
                payment.getInvoice().getInvoiceNumber(),
                payment.getAmount(),
                payment.getPaymentDate(),
                payment.getPaymentMethod(),
                payment.getReference(),
                payment.getCreatedAt());
    }
}
