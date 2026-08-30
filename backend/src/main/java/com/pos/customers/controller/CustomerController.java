package com.pos.customers.controller;

import com.pos.common.config.RequestCorrelation;
import com.pos.common.response.ApiResponse;
import com.pos.customers.dto.CustomerCreateRequest;
import com.pos.customers.dto.CustomerResponse;
import com.pos.customers.dto.CustomerUpdateRequest;
import com.pos.customers.service.CustomerService;
import com.pos.sales.dto.SaleSummaryResponse;
import com.pos.sales.service.SaleService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;
    private final SaleService saleService;

    public CustomerController(CustomerService customerService, SaleService saleService) {
        this.customerService = customerService;
        this.saleService = saleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER_READ')") // customers list
    public ApiResponse<Page<CustomerResponse>> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Boolean isActive,
            Pageable pageable) {
        return ApiResponse.of(customerService.search(query, isActive, pageable), RequestCorrelation.currentId());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CUSTOMER_WRITE')") // customers create
    public ApiResponse<CustomerResponse> create(@Valid @RequestBody CustomerCreateRequest request) {
        return ApiResponse.of(customerService.create(request), RequestCorrelation.currentId());
    }

    @GetMapping("/{id}/sales")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    public ApiResponse<Page<SaleSummaryResponse>> sales(@PathVariable UUID id, Pageable pageable) {
        return ApiResponse.of(saleService.listForCustomer(id, pageable), RequestCorrelation.currentId());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    public ApiResponse<CustomerResponse> get(@PathVariable UUID id) {
        return ApiResponse.of(customerService.get(id), RequestCorrelation.currentId());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_WRITE')")
    public ApiResponse<CustomerResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody CustomerUpdateRequest request) {
        return ApiResponse.of(customerService.update(id, request), RequestCorrelation.currentId());
    }
}
