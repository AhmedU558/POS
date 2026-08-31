package com.pos.promotions.dto;

import com.pos.promotions.domain.Promotion;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PromotionRuleResponse(
        UUID id,
        String ruleType,
        String ruleValue
) {}
