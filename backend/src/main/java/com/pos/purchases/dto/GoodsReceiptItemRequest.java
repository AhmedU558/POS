package com.pos.purchases.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record GoodsReceiptItemRequest(
        @NotNull UUID productId,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
        @Size(max = 100) String batchNumber,
        LocalDate expirationDate,
        LocalDate manufacturingDate
) {
}
