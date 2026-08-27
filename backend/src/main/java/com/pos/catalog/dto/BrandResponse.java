package com.pos.catalog.dto;

import com.pos.catalog.entity.Brand;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BrandResponse(
        UUID id,
        String name,
        String description,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static BrandResponse from(Brand b) {
        return new BrandResponse(
                b.getId(),
                b.getName(),
                b.getDescription(),
                b.isActive(),
                b.getCreatedAt(),
                b.getUpdatedAt()
        );
    }
}
