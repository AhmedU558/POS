package com.pos.inventory.controller;

import com.pos.common.config.RequestCorrelation;
import com.pos.common.response.ApiResponse;
import com.pos.inventory.dto.InventoryAdjustmentRequest;
import com.pos.inventory.dto.InventoryBalanceResponse;
import com.pos.inventory.dto.InventoryReceiptRequest;
import com.pos.inventory.dto.InventoryTransactionResponse;
import com.pos.inventory.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    public ApiResponse<Page<InventoryBalanceResponse>> getBalances(
            @RequestParam UUID storeId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String query,
            Pageable pageable) {
        return ApiResponse.of(inventoryService.searchBalances(storeId, categoryId, query, pageable), RequestCorrelation.currentId());
    }

    @GetMapping("/{productId}")
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    public ApiResponse<InventoryBalanceResponse> getBalance(
            @PathVariable UUID productId,
            @RequestParam UUID storeId) {
        return ApiResponse.of(inventoryService.getBalance(storeId, productId), RequestCorrelation.currentId());
    }

    @GetMapping("/{productId}/movements")
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    public ApiResponse<Page<InventoryTransactionResponse>> getMovements(
            @PathVariable UUID productId,
            @RequestParam UUID storeId,
            Pageable pageable) {
        return ApiResponse.of(inventoryService.getMovements(storeId, productId, pageable), RequestCorrelation.currentId());
    }

    @PostMapping("/adjustments")
    @PreAuthorize("hasAuthority('INVENTORY_ADJUST')")
    public ApiResponse<InventoryBalanceResponse> adjustStock(@Valid @RequestBody InventoryAdjustmentRequest request) {
        return ApiResponse.of(inventoryService.adjustStock(request), RequestCorrelation.currentId());
    }

    @PostMapping("/receipts")
    @PreAuthorize("hasAuthority('INVENTORY_RECEIVE')")
    public ApiResponse<InventoryBalanceResponse> receiveStock(@Valid @RequestBody InventoryReceiptRequest request) {
        return ApiResponse.of(inventoryService.receiveStock(request), RequestCorrelation.currentId());
    }
}