package com.pos.promotions.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PromotionRequest(
        @NotNull UUID storeId,
        @NotBlank String name,
        String description,
        @NotBlank String type,
        @NotNull BigDecimal discountValue,
        @NotNull OffsetDateTime startDate,
        @NotNull OffsetDateTime endDate,
        boolean active,
        int priority,
        boolean stackable,
        @Valid List<PromotionRuleRequest> rules
) {}
