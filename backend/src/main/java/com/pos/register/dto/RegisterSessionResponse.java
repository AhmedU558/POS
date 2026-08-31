package com.pos.register.dto;

import com.pos.organization.domain.RegisterSession;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RegisterSessionResponse(
        UUID id,
        UUID registerId,
        UUID storeId,
        UUID terminalId,
        UUID cashierId,
        String status,
        BigDecimal openingCash,
        OffsetDateTime openedAt,
        OffsetDateTime closedAt
) {
    public static RegisterSessionResponse fromEntity(RegisterSession session) {
        return new RegisterSessionResponse(
                session.getId(),
                session.getRegister().getId(),
                session.getRegister().getStore().getId(),
                session.getRegister().getTerminal().getId(),
                session.getCashier().getId(),
                session.getStatus(),
                session.getOpeningCash(),
                session.getOpenedAt(),
                session.getClosedAt());
    }
}
