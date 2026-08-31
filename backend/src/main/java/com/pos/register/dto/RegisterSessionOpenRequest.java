package com.pos.register.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RegisterSessionOpenRequest(
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal openingCash
) {
}
