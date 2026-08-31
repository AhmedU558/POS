package com.pos.orders.dto;

import com.pos.orders.domain.OnlineOrder;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public record OnlineOrderResponse(
        UUID id,
        UUID storeId,
        UUID customerId,
        String channel,
        String externalOrderId,
        String status,
        BigDecimal subtotal,
        BigDecimal discountTotal,
        BigDecimal taxTotal,
        BigDecimal grandTotal,
        String currencyCode,
        String notes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<OnlineOrderItemResponse> items
) {
    public static OnlineOrderResponse fromEntity(OnlineOrder order) {
        return new OnlineOrderResponse(
                order.getId(),
                order.getStore().getId(),
                order.getCustomer() != null ? order.getCustomer().getId() : null,
                order.getChannel(),
                order.getExternalOrderId(),
                order.getStatus(),
                order.getSubtotal(),
                order.getDiscountTotal(),
                order.getTaxTotal(),
                order.getGrandTotal(),
                order.getCurrencyCode(),
                order.getNotes(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getItems().stream()
                        .map(OnlineOrderItemResponse::fromEntity)
                        .collect(Collectors.toList())
        );
    }
}
