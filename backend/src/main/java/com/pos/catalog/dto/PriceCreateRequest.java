package com.pos.catalog.dto;

import com.pos.catalog.entity.PriceType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PriceCreateRequest(
    @NotNull PriceType priceType,
    @NotNull @DecimalMin("0.0") BigDecimal amount,
    @NotNull OffsetDateTime effectiveFrom,
    OffsetDateTime effectiveTo
) {}
