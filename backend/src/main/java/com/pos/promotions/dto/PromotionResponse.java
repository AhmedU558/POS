package com.pos.promotions.dto;

import com.pos.promotions.domain.Promotion;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public record PromotionResponse(
        UUID id,
        UUID storeId,
        String name,
        String description,
        String type,
        BigDecimal discountValue,
        OffsetDateTime startDate,
        OffsetDateTime endDate,
        boolean active,
        int priority,
        boolean stackable,
        List<PromotionRuleResponse> rules
) {
    public static PromotionResponse fromEntity(Promotion promotion) {
        return new PromotionResponse(
                promotion.getId(),
                promotion.getStore().getId(),
                promotion.getName(),
                promotion.getDescription(),
                promotion.getType(),
                promotion.getDiscountValue(),
                promotion.getStartDate(),
                promotion.getEndDate(),
                promotion.isActive(),
                promotion.getPriority(),
                promotion.isStackable(),
                promotion.getRules().stream()
                        .map(rule -> new PromotionRuleResponse(
                                rule.getId(),
                                rule.getRuleType(),
                                rule.getRuleValue()
                        ))
                        .collect(Collectors.toList())
        );
    }
}
