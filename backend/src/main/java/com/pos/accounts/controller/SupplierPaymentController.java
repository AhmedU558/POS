package com.pos.accounts.controller;

import com.pos.accounts.dto.PayablesSummaryResponse;
import com.pos.accounts.dto.SupplierInvoiceResponse;
import com.pos.accounts.dto.SupplierPaymentCreateRequest;
import com.pos.accounts.dto.SupplierPaymentResponse;
import com.pos.accounts.service.SupplierPaymentService;
import com.pos.common.config.RequestCorrelation;
import com.pos.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts-payable")
public class SupplierPaymentController {

    private final SupplierPaymentService supplierPaymentService;

    public SupplierPaymentController(SupplierPaymentService supplierPaymentService) {
        this.supplierPaymentService = supplierPaymentService;
    }

    @GetMapping("/payments")
    @PreAuthorize("hasAuthority('AP_READ')") // ap payments list
    public ApiResponse<Page<SupplierPaymentResponse>> list(
            @RequestParam(required = false) UUID invoiceId,
            Pageable pageable) {
        return ApiResponse.of(supplierPaymentService.search(invoiceId, pageable), RequestCorrelation.currentId());
    }

    @PostMapping("/payments")
    @PreAuthorize("hasAuthority('AP_PAYMENT_CREATE')") // ap payments create
    public ApiResponse<SupplierPaymentResponse> create(@Valid @RequestBody SupplierPaymentCreateRequest request) {
        return ApiResponse.of(supplierPaymentService.create(request), RequestCorrelation.currentId());
    }

    @GetMapping("/overdue")
    @PreAuthorize("hasAuthority('AP_READ')") // ap overdue list
    public ApiResponse<Page<SupplierInvoiceResponse>> overdue(Pageable pageable) {
        return ApiResponse.of(supplierPaymentService.overdue(pageable), RequestCorrelation.currentId());
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('AP_READ')") // ap summary
    public ApiResponse<PayablesSummaryResponse> summary() {
        return ApiResponse.of(supplierPaymentService.summary(), RequestCorrelation.currentId());
    }
}
