package com.pos.organization.dto;

import com.pos.organization.domain.Terminal;
import java.time.Instant;
import java.util.UUID;

public record TerminalResponse(
        UUID id,
        UUID storeId,
        String code,
        String name,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static TerminalResponse from(Terminal terminal) {
        return new TerminalResponse(
                terminal.getId(),
                terminal.getStore().getId(),
                terminal.getCode(),
                terminal.getName(),
                terminal.getStatus(),
                terminal.getCreatedAt(),
                terminal.getUpdatedAt()
        );
    }
}
