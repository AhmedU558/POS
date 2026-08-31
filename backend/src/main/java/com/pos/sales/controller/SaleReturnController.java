package com.pos.sales.controller;

import com.pos.sales.dto.SaleReturnRequest;
import com.pos.sales.dto.SaleReturnResponse;
import com.pos.sales.service.SaleReturnService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sales")
public class SaleReturnController {

    private final SaleReturnService saleReturnService;

    public SaleReturnController(SaleReturnService saleReturnService) {
        this.saleReturnService = saleReturnService;
    }

    @PostMapping("/{id}/return")
    @PreAuthorize("hasAuthority('SALE_REFUND')")
    public ResponseEntity<SaleReturnResponse> processReturn(
            @PathVariable UUID id,
            @RequestBody @Valid SaleReturnRequest request) {
        return ResponseEntity.ok(saleReturnService.processReturn(id, request));
    }
}
