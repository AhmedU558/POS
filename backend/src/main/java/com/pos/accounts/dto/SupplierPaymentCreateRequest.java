package com.pos.accounts.dto;

import com.pos.accounts.domain.SupplierPaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SupplierPaymentCreateRequest(
        @NotNull UUID invoiceId,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
        @NotNull LocalDate paymentDate,
        @NotNull SupplierPaymentMethod method,
        String reference
) {
}
