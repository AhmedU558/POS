package com.pos.inventory.controller;

import com.pos.common.config.RequestCorrelation;
import com.pos.common.response.ApiResponse;
import com.pos.inventory.dto.InventoryAdjustmentRequest;
import com.pos.inventory.dto.InventoryBalanceResponse;
import com.pos.inventory.dto.InventoryBatchResponse;
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

    @GetMapping("/batches")
    @PreAuthorize("hasAuthority('INVENTORY_READ')") // batches list
    public ApiResponse<Page<InventoryBatchResponse>> listBatches(
            @RequestParam UUID storeId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) Integer days,
            Pageable pageable) {
        return ApiResponse.of(inventoryService.listBatches(storeId, productId, days, pageable), RequestCorrelation.currentId());
    }

    @GetMapping("/expiry")
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    public ApiResponse<Page<InventoryBatchResponse>> listExpiry(
            @RequestParam UUID storeId,
            @RequestParam(required = false) Integer days,
            Pageable pageable) {
        return ApiResponse.of(inventoryService.listExpiry(storeId, days, pageable), RequestCorrelation.currentId());
    }

    @GetMapping("/{productId:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}}")
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    public ApiResponse<InventoryBalanceResponse> getBalance(
            @PathVariable UUID productId,
            @RequestParam UUID storeId) {
        return ApiResponse.of(inventoryService.getBalance(storeId, productId), RequestCorrelation.currentId());
    }

    @GetMapping("/{productId:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}}/movements")
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