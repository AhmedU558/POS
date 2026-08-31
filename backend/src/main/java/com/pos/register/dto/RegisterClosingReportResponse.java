package com.pos.register.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RegisterClosingReportResponse(
        UUID sessionId,
        String zReportNumber,
        String status,
        BigDecimal openingCash,
        BigDecimal cashInTotal,
        BigDecimal cashOutTotal,
        BigDecimal cashSalesTotal,
        BigDecimal expectedCash,
        BigDecimal actualCash,
        BigDecimal variance,
        String notes,
        OffsetDateTime openedAt,
        OffsetDateTime closedAt
) {
}
