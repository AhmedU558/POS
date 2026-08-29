package com.pos.catalog.dto;

import com.pos.catalog.entity.ProductPrice;
import com.pos.catalog.entity.PriceType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PriceResponse(
    UUID id,
    UUID productId,
    PriceType priceType,
    BigDecimal amount,
    OffsetDateTime effectiveFrom,
    OffsetDateTime effectiveTo,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static PriceResponse fromEntity(ProductPrice pp) {
        return new PriceResponse(
            pp.getId(),
            pp.getProduct().getId(),
            pp.getPriceType(),
            pp.getAmount(),
            pp.getEffectiveFrom(),
            pp.getEffectiveTo(),
            pp.getCreatedAt(),
            pp.getUpdatedAt()
        );
    }
}
