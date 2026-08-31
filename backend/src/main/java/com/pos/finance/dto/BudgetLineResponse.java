package com.pos.finance.dto;

import com.pos.finance.domain.BudgetLine;
import java.math.BigDecimal;
import java.util.UUID;

public record BudgetLineResponse(
        UUID id,
        String category,
        BigDecimal allocatedAmount
) {
    public static BudgetLineResponse fromEntity(BudgetLine line) {
        return new BudgetLineResponse(
                line.getId(),
                line.getCategory(),
                line.getAllocatedAmount()
        );
    }
}
