package com.pos.accounts.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SupplierInvoiceUpdateRequest(
        @NotBlank String invoiceNumber,
        @NotNull LocalDate invoiceDate,
        @NotNull LocalDate dueDate,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal totalAmount,
        String notes
) {
}
