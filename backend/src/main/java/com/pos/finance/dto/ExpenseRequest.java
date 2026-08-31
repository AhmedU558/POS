package com.pos.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseRequest(
        @NotNull UUID storeId,
        @NotBlank String category,
        @NotNull @DecimalMin(value = "0") BigDecimal amount,
        @NotNull LocalDate expenseDate,
        String description
) {}
