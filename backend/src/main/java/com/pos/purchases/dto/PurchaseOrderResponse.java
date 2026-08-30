package com.pos.purchases.dto;

import com.pos.purchases.domain.PurchaseOrder;
import com.pos.purchases.domain.PurchaseOrderStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderResponse(
        UUID id,
        String poNumber,
        UUID supplierId,
        String supplierName,
        PurchaseOrderStatus status,
        String notes,
        List<PurchaseOrderItemResponse> items,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static PurchaseOrderResponse fromEntity(PurchaseOrder order) {
        return new PurchaseOrderResponse(
                order.getId(),
                order.getPoNumber(),
                order.getSupplier().getId(),
                order.getSupplier().getName(),
                order.getStatus(),
                order.getNotes(),
                order.getItems().stream().map(PurchaseOrderItemResponse::fromEntity).toList(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    public static PurchaseOrderResponse fromEntityWithoutItems(PurchaseOrder order) {
        return new PurchaseOrderResponse(
                order.getId(),
                order.getPoNumber(),
                order.getSupplier().getId(),
                order.getSupplier().getName(),
                order.getStatus(),
                order.getNotes(),
                List.of(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
