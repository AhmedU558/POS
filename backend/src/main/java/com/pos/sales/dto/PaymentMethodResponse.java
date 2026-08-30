package com.pos.sales.dto;

import com.pos.sales.domain.PaymentMethod;

import java.util.UUID;

public record PaymentMethodResponse(
        UUID id,
        String code,
        String name,
        String type,
        boolean active
) {
    public static PaymentMethodResponse fromEntity(PaymentMethod method) {
        return new PaymentMethodResponse(
                method.getId(),
                method.getCode(),
                method.getName(),
                method.getType(),
                method.isActive());
    }
}
