package com.pos.finance.dto;

import com.pos.finance.domain.Expense;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ExpenseResponse(
        UUID id,
        UUID storeId,
        String category,
        BigDecimal amount,
        LocalDate expenseDate,
        String description,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        UUID createdBy
) {
    public static ExpenseResponse fromEntity(Expense expense) {
        return new ExpenseResponse(
                expense.getId(),
                expense.getStore().getId(),
                expense.getCategory(),
                expense.getAmount(),
                expense.getExpenseDate(),
                expense.getDescription(),
                expense.getCreatedAt(),
                expense.getUpdatedAt(),
                expense.getCreatedBy().getId()
        );
    }
}
