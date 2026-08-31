package com.pos.finance.dto;

import com.pos.finance.domain.Budget;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public record BudgetResponse(
        UUID id,
        UUID storeId,
        String name,
        LocalDate periodStart,
        LocalDate periodEnd,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<BudgetLineResponse> lines
) {
    public static BudgetResponse fromEntity(Budget budget) {
        return new BudgetResponse(
                budget.getId(),
                budget.getStore().getId(),
                budget.getName(),
                budget.getPeriodStart(),
                budget.getPeriodEnd(),
                budget.getStatus(),
                budget.getCreatedAt(),
                budget.getUpdatedAt(),
                budget.getLines().stream()
                        .map(BudgetLineResponse::fromEntity)
                        .collect(Collectors.toList())
        );
    }
}
