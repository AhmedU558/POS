package com.pos.customers.controller;

import com.pos.common.config.RequestCorrelation;
import com.pos.common.response.ApiResponse;
import com.pos.customers.dto.CustomerCreditResponse;
import com.pos.customers.dto.CustomerCreditTransactionRequest;
import com.pos.customers.service.CustomerCreditService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/credit")
public class CustomerCreditController {

    private final CustomerCreditService customerCreditService;

    public CustomerCreditController(CustomerCreditService customerCreditService) {
        this.customerCreditService = customerCreditService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CREDIT_READ')") // credit get
    public ApiResponse<CustomerCreditResponse> get(@PathVariable UUID customerId, Pageable pageable) {
        return ApiResponse.of(customerCreditService.get(customerId, pageable), RequestCorrelation.currentId());
    }

    @PostMapping("/transactions")
    @PreAuthorize("hasAuthority('CREDIT_WRITE')") // credit post
    public ApiResponse<CustomerCreditResponse> post(
            @PathVariable UUID customerId,
            @Valid @RequestBody CustomerCreditTransactionRequest request) {
        return ApiResponse.of(customerCreditService.post(customerId, request), RequestCorrelation.currentId());
    }
}
