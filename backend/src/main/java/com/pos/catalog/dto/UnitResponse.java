package com.pos.catalog.dto;

import com.pos.catalog.entity.Unit;
import java.time.OffsetDateTime;
import java.util.UUID;

public record UnitResponse(
        UUID id,
        String code,
        String name,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static UnitResponse from(Unit u) {
        return new UnitResponse(
                u.getId(),
                u.getCode(),
                u.getName(),
                u.isActive(),
                u.getCreatedAt(),
                u.getUpdatedAt()
        );
    }
}
