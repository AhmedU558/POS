package com.pos.register.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RegisterSessionSummaryResponse(
        UUID id,
        UUID registerId,
        UUID storeId,
        UUID terminalId,
        UUID cashierId,
        String status,
        BigDecimal openingCash,
        BigDecimal cashInTotal,
        BigDecimal cashOutTotal,
        BigDecimal cashSalesTotal,
        BigDecimal expectedCash,
        OffsetDateTime openedAt,
        OffsetDateTime closedAt
) {
}
