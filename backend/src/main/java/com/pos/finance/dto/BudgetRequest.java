package com.pos.finance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record BudgetRequest(
        @NotNull UUID storeId,
        @NotBlank String name,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        @NotEmpty @Valid List<BudgetLineRequest> lines
) {}
