package com.pos.purchases.dto;

import com.pos.purchases.domain.PurchaseOrderItem;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseOrderItemResponse(
        UUID id,
        UUID productId,
        String sku,
        String name,
        BigDecimal quantity
) {
    public static PurchaseOrderItemResponse fromEntity(PurchaseOrderItem item) {
        return new PurchaseOrderItemResponse(
                item.getId(),
                item.getProduct().getId(),
                item.getProduct().getSku(),
                item.getProduct().getName(),
                item.getQuantity());
    }
}
