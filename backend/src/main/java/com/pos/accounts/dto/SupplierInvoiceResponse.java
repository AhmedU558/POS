package com.pos.accounts.dto;

import com.pos.accounts.domain.SupplierInvoice;
import com.pos.accounts.domain.SupplierInvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SupplierInvoiceResponse(
        UUID id,
        String invoiceNumber,
        UUID supplierId,
        String supplierName,
        LocalDate invoiceDate,
        LocalDate dueDate,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal remainingAmount,
        SupplierInvoiceStatus status,
        String notes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static SupplierInvoiceResponse fromEntity(SupplierInvoice invoice) {
        return new SupplierInvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getSupplier().getId(),
                invoice.getSupplier().getName(),
                invoice.getInvoiceDate(),
                invoice.getDueDate(),
                invoice.getTotalAmount(),
                invoice.getPaidAmount(),
                invoice.remainingAmount(),
                invoice.getStatus(),
                invoice.getNotes(),
                invoice.getCreatedAt(),
                invoice.getUpdatedAt());
    }
}
