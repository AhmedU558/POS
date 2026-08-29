package com.pos.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BarcodeCreateRequest(
    @NotBlank String barcode,
    @NotNull Boolean isPrimary
) {}
