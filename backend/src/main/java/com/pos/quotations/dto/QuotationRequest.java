package com.pos.quotations.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import com.pos.sales.dto.SaleItemRequest;

public record QuotationRequest(
        @NotNull UUID storeId,
        UUID customerId,
        @NotEmpty @Valid List<SaleItemRequest> items,
        OffsetDateTime expirationDate,
        String notes
) {
}
