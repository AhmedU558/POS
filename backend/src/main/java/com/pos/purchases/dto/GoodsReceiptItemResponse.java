package com.pos.purchases.dto;

import com.pos.purchases.domain.GoodsReceiptItem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record GoodsReceiptItemResponse(
        UUID id,
        UUID productId,
        String sku,
        String name,
        BigDecimal quantity,
        String batchNumber,
        LocalDate expirationDate,
        LocalDate manufacturingDate
) {
    public static GoodsReceiptItemResponse fromEntity(GoodsReceiptItem item) {
        return new GoodsReceiptItemResponse(
                item.getId(),
                item.getProduct().getId(),
                item.getProduct().getSku(),
                item.getProduct().getName(),
                item.getQuantity(),
                item.getBatchNumber(),
                item.getExpirationDate(),
                item.getManufacturingDate());
    }
}
