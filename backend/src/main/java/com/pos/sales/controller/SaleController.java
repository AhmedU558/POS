package com.pos.sales.controller;

import com.pos.common.config.RequestCorrelation;
import com.pos.common.response.ApiResponse;
import com.pos.sales.dto.SaleCreateRequest;
import com.pos.sales.dto.SaleResponse;
import com.pos.sales.service.SaleService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sales")
public class SaleController {

    private final SaleService saleService;

    public SaleController(SaleService saleService) {
        this.saleService = saleService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SALE_CREATE')") // pos complete sale
    public ApiResponse<SaleResponse> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SaleCreateRequest request) {
        return ApiResponse.of(saleService.create(request, idempotencyKey), RequestCorrelation.currentId());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SALE_READ')")
    public ApiResponse<SaleResponse> get(@PathVariable UUID id) {
        return ApiResponse.of(saleService.get(id), RequestCorrelation.currentId());
    }
}
