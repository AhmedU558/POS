package com.pos.purchases.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record PurchaseOrderWriteRequest(
        @NotBlank String poNumber,
        @NotNull UUID supplierId,
        String notes,
        @NotNull List<@Valid @NotNull PurchaseOrderItemRequest> items
) {
}
