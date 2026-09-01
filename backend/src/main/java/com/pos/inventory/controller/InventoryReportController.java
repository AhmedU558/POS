package com.pos.inventory.controller;

import com.pos.common.config.RequestCorrelation;
import com.pos.common.response.ApiResponse;
import com.pos.inventory.dto.InventoryBatchResponse;
import com.pos.inventory.dto.InventoryReportRow;
import com.pos.inventory.dto.InventoryTransactionResponse;
import com.pos.inventory.service.InventoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports/inventory")
public class InventoryReportController {

    private final InventoryService inventoryService;

    public InventoryReportController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('REPORT_INVENTORY')")
    public ApiResponse<Page<InventoryReportRow>> inventory(
            @RequestParam UUID storeId,
            @RequestParam(required = false, defaultValue = "false") boolean lowStockOnly,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String query,
            Pageable pageable) {
        return ApiResponse.of(inventoryService.reportInventory(storeId, lowStockOnly, categoryId, query, pageable), RequestCorrelation.currentId());
    }

    @GetMapping("/movements")
    @PreAuthorize("hasAuthority('REPORT_INVENTORY')")
    public ApiResponse<Page<InventoryTransactionResponse>> movements(
            @RequestParam UUID storeId,
            @RequestParam(required = false) UUID productId,
            Pageable pageable) {
        return ApiResponse.of(inventoryService.reportMovements(storeId, productId, pageable), RequestCorrelation.currentId());
    }

    @GetMapping("/expiry")
    @PreAuthorize("hasAuthority('REPORT_INVENTORY')")
    public ApiResponse<Page<InventoryBatchResponse>> expiry(
            @RequestParam UUID storeId,
            @RequestParam(required = false) Integer days,
            Pageable pageable) {
        return ApiResponse.of(inventoryService.reportExpiry(storeId, days, pageable), RequestCorrelation.currentId());
    }
}
