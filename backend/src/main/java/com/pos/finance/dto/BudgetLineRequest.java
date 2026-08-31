package com.pos.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record BudgetLineRequest(
        @NotBlank String category,
        @NotNull @DecimalMin(value = "0") BigDecimal allocatedAmount
) {}
