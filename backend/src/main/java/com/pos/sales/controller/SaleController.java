package com.pos.sales.controller;

import com.pos.common.config.RequestCorrelation;
import com.pos.common.response.ApiResponse;
import com.pos.sales.dto.SaleCreateRequest;
import com.pos.sales.dto.SaleReceiptResponse;
import com.pos.sales.dto.SaleResponse;
import com.pos.sales.dto.SaleResumeRequest;
import com.pos.sales.dto.SaleSummaryResponse;
import com.pos.sales.service.SaleService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sales")
public class SaleController {

    private final SaleService saleService;

    public SaleController(SaleService saleService) {
        this.saleService = saleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SALE_READ')") // pos sales history
    public ApiResponse<Page<SaleSummaryResponse>> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID cashierId,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            Pageable pageable) {
        return ApiResponse.of(
                saleService.search(query, status, customerId, cashierId, from, to, pageable),
                RequestCorrelation.currentId());
    }

    @GetMapping("/{id}/receipt")
    @PreAuthorize("hasAuthority('RECEIPT_READ')")
    public ApiResponse<SaleReceiptResponse> receipt(@PathVariable UUID id) {
        return ApiResponse.of(saleService.receipt(id), RequestCorrelation.currentId());
    }

    @PostMapping("/{id}/receipt/reprint")
    @PreAuthorize("hasAuthority('RECEIPT_REPRINT')")
    public ApiResponse<SaleReceiptResponse> reprint(@PathVariable UUID id) {
        return ApiResponse.of(saleService.reprint(id), RequestCorrelation.currentId());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SALE_CREATE')") // pos complete sale
    public ApiResponse<SaleResponse> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SaleCreateRequest request) {
        return ApiResponse.of(saleService.create(request, idempotencyKey), RequestCorrelation.currentId());
    }

    @PostMapping("/{id}/hold")
    @PreAuthorize("hasAuthority('SALE_CREATE')")
    public ApiResponse<SaleResponse> hold(@PathVariable UUID id) {
        return ApiResponse.of(saleService.hold(id), RequestCorrelation.currentId());
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize("hasAuthority('SALE_CREATE')")
    public ApiResponse<SaleResponse> resume(
            @PathVariable UUID id,
            @Valid @RequestBody SaleResumeRequest request) {
        return ApiResponse.of(saleService.resume(id, request), RequestCorrelation.currentId());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SALE_READ')")
    public ApiResponse<SaleResponse> get(@PathVariable UUID id) {
        return ApiResponse.of(saleService.get(id), RequestCorrelation.currentId());
    }
}
