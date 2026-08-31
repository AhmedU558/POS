package com.pos.register.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RegisterSessionCloseRequest(
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal actualCash,
        @Size(max = 255) String notes
) {
}
