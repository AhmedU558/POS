package com.pos.sales.controller;

import com.pos.common.config.RequestCorrelation;
import com.pos.common.response.ApiResponse;
import com.pos.sales.dto.PaymentMethodResponse;
import com.pos.sales.repository.PaymentMethodRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payment-methods")
public class PaymentMethodController {

    private final PaymentMethodRepository paymentMethodRepository;

    public PaymentMethodController(PaymentMethodRepository paymentMethodRepository) {
        this.paymentMethodRepository = paymentMethodRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PAYMENT_READ')") // pos payment methods
    public ApiResponse<List<PaymentMethodResponse>> list() {
        return ApiResponse.of(
                paymentMethodRepository.findByActiveTrueOrderByCodeAsc().stream()
                        .map(PaymentMethodResponse::fromEntity)
                        .toList(),
                RequestCorrelation.currentId());
    }
}
