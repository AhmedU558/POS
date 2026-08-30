package com.pos.purchases.controller;

import com.pos.common.config.RequestCorrelation;
import com.pos.common.response.ApiResponse;
import com.pos.purchases.domain.PurchaseOrderStatus;
import com.pos.purchases.dto.PurchaseOrderResponse;
import com.pos.purchases.dto.PurchaseOrderWriteRequest;
import com.pos.purchases.service.PurchaseOrderService;
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
@RequestMapping("/api/v1/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    public PurchaseOrderController(PurchaseOrderService purchaseOrderService) {
        this.purchaseOrderService = purchaseOrderService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PURCHASE_READ')") // purchase orders list
    public ApiResponse<Page<PurchaseOrderResponse>> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) PurchaseOrderStatus status,
            Pageable pageable) {
        return ApiResponse.of(purchaseOrderService.search(query, status, pageable), RequestCorrelation.currentId());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PURCHASE_WRITE')") // purchase orders create
    public ApiResponse<PurchaseOrderResponse> create(@Valid @RequestBody PurchaseOrderWriteRequest request) {
        return ApiResponse.of(purchaseOrderService.create(request), RequestCorrelation.currentId());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PURCHASE_READ')")
    public ApiResponse<PurchaseOrderResponse> get(@PathVariable UUID id) {
        return ApiResponse.of(purchaseOrderService.get(id), RequestCorrelation.currentId());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('PURCHASE_WRITE')")
    public ApiResponse<PurchaseOrderResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody PurchaseOrderWriteRequest request) {
        return ApiResponse.of(purchaseOrderService.update(id, request), RequestCorrelation.currentId());
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('PURCHASE_APPROVE')") // purchase orders submit
    public ApiResponse<PurchaseOrderResponse> submit(@PathVariable UUID id) {
        return ApiResponse.of(purchaseOrderService.submit(id), RequestCorrelation.currentId());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('PURCHASE_APPROVE')") // purchase orders cancel
    public ApiResponse<PurchaseOrderResponse> cancel(@PathVariable UUID id) {
        return ApiResponse.of(purchaseOrderService.cancel(id), RequestCorrelation.currentId());
    }
}
