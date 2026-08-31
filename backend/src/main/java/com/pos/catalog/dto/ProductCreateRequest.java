package com.pos.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record ProductCreateRequest(
    @NotBlank String sku,
    @NotBlank String name,
    String description,
    UUID categoryId,
    UUID brandId,
    UUID unitId,
    @NotNull @DecimalMin("0.0") BigDecimal purchasePrice,
    @NotNull @DecimalMin("0.0") BigDecimal sellingPrice,
    @DecimalMin("0.0") BigDecimal wholesalePrice,
    @NotNull @DecimalMin("0.0") BigDecimal taxRate,
    @NotNull @DecimalMin("0.0") BigDecimal minStock,
    @DecimalMin("0.0") BigDecimal maxStock,
    @NotNull Boolean trackBatch,
    @NotNull Boolean trackExpiry,
    @NotNull Boolean isActive,
    String imageUrl
) {}
