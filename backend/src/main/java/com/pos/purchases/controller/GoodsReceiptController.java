package com.pos.purchases.controller;

import com.pos.common.config.RequestCorrelation;
import com.pos.common.response.ApiResponse;
import com.pos.purchases.dto.GoodsReceiptCreateRequest;
import com.pos.purchases.dto.GoodsReceiptResponse;
import com.pos.purchases.service.GoodsReceiptService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/goods-receipts")
public class GoodsReceiptController {

    private final GoodsReceiptService goodsReceiptService;

    public GoodsReceiptController(GoodsReceiptService goodsReceiptService) {
        this.goodsReceiptService = goodsReceiptService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('INVENTORY_RECEIVE')") // goods receipts create
    public ApiResponse<GoodsReceiptResponse> create(@Valid @RequestBody GoodsReceiptCreateRequest request) {
        return ApiResponse.of(goodsReceiptService.create(request), RequestCorrelation.currentId());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_READ')") // goods receipts get
    public ApiResponse<GoodsReceiptResponse> get(@PathVariable UUID id) {
        return ApiResponse.of(goodsReceiptService.get(id), RequestCorrelation.currentId());
    }
}
