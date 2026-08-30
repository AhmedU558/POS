package com.pos.purchases.dto;

import com.pos.purchases.domain.GoodsReceipt;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record GoodsReceiptResponse(
        UUID id,
        UUID purchaseOrderId,
        UUID storeId,
        List<GoodsReceiptItemResponse> items,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static GoodsReceiptResponse fromEntity(GoodsReceipt receipt) {
        return new GoodsReceiptResponse(
                receipt.getId(),
                receipt.getPurchaseOrder().getId(),
                receipt.getStore().getId(),
                receipt.getItems().stream().map(GoodsReceiptItemResponse::fromEntity).toList(),
                receipt.getCreatedAt(),
                receipt.getUpdatedAt());
    }
}
