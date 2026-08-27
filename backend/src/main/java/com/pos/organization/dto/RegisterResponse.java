package com.pos.organization.dto;

import com.pos.organization.domain.Register;
import java.time.Instant;
import java.util.UUID;

public record RegisterResponse(
        UUID id,
        UUID storeId,
        UUID terminalId,
        String code,
        String name,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static RegisterResponse from(Register register) {
        return new RegisterResponse(
                register.getId(),
                register.getStore().getId(),
                register.getTerminal().getId(),
                register.getCode(),
                register.getName(),
                register.getStatus(),
                register.getCreatedAt(),
                register.getUpdatedAt()
        );
    }
}
