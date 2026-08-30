package com.pos.suppliers.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record SupplierProductsReplaceRequest(
        @NotNull List<@NotNull UUID> productIds
) {
}
