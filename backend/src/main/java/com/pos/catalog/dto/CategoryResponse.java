package com.pos.catalog.dto;

import com.pos.catalog.entity.Category;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        UUID parentId,
        String name,
        String description,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static CategoryResponse from(Category c) {
        return new CategoryResponse(
                c.getId(),
                c.getParent() != null ? c.getParent().getId() : null,
                c.getName(),
                c.getDescription(),
                c.isActive(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
