package com.pos.accounts.controller;

import com.pos.accounts.domain.SupplierInvoiceStatus;
import com.pos.accounts.dto.SupplierInvoiceCreateRequest;
import com.pos.accounts.dto.SupplierInvoiceResponse;
import com.pos.accounts.dto.SupplierInvoiceUpdateRequest;
import com.pos.accounts.service.SupplierInvoiceService;
import com.pos.common.config.RequestCorrelation;
import com.pos.common.response.ApiResponse;
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
@RequestMapping("/api/v1/accounts-payable/invoices")
public class SupplierInvoiceController {

    private final SupplierInvoiceService supplierInvoiceService;

    public SupplierInvoiceController(SupplierInvoiceService supplierInvoiceService) {
        this.supplierInvoiceService = supplierInvoiceService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('AP_READ')") // ap invoices list
    public ApiResponse<Page<SupplierInvoiceResponse>> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) SupplierInvoiceStatus status,
            Pageable pageable) {
        return ApiResponse.of(supplierInvoiceService.search(query, status, pageable), RequestCorrelation.currentId());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('AP_WRITE')") // ap invoices create
    public ApiResponse<SupplierInvoiceResponse> create(@Valid @RequestBody SupplierInvoiceCreateRequest request) {
        return ApiResponse.of(supplierInvoiceService.create(request), RequestCorrelation.currentId());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('AP_READ')")
    public ApiResponse<SupplierInvoiceResponse> get(@PathVariable UUID id) {
        return ApiResponse.of(supplierInvoiceService.get(id), RequestCorrelation.currentId());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('AP_WRITE')")
    public ApiResponse<SupplierInvoiceResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody SupplierInvoiceUpdateRequest request) {
        return ApiResponse.of(supplierInvoiceService.update(id, request), RequestCorrelation.currentId());
    }
}
