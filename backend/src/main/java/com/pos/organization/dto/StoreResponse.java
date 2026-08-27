package com.pos.organization.dto;

import com.pos.organization.domain.Store;
import java.time.Instant;
import java.util.UUID;

public record StoreResponse(
        UUID id,
        String code,
        String name,
        String currencyCode,
        String timezone,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static StoreResponse from(Store store) {
        return new StoreResponse(
                store.getId(),
                store.getCode(),
                store.getName(),
                store.getCurrencyCode(),
                store.getTimezone(),
                store.isActive(),
                store.getCreatedAt(),
                store.getUpdatedAt()
        );
    }
}
