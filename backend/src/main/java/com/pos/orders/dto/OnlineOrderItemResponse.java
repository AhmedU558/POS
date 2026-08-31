package com.pos.orders.dto;

import com.pos.orders.domain.OnlineOrder;
import com.pos.orders.domain.OnlineOrderItem;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public record OnlineOrderItemResponse(
        UUID id,
        UUID productId,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal lineTotal
) {
    public static OnlineOrderItemResponse fromEntity(OnlineOrderItem item) {
        return new OnlineOrderItemResponse(
                item.getId(),
                item.getProduct().getId(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getDiscountAmount(),
                item.getTaxAmount(),
                item.getLineTotal()
        );
    }
}
