package com.pos.suppliers.controller;

import com.pos.common.config.RequestCorrelation;
import com.pos.common.response.ApiResponse;
import com.pos.suppliers.dto.SupplierCreateRequest;
import com.pos.suppliers.dto.SupplierResponse;
import com.pos.suppliers.dto.SupplierUpdateRequest;
import com.pos.suppliers.service.SupplierService;
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
@RequestMapping("/api/v1/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SUPPLIER_READ')") // suppliers list
    public ApiResponse<Page<SupplierResponse>> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Boolean isActive,
            Pageable pageable) {
        return ApiResponse.of(supplierService.search(query, isActive, pageable), RequestCorrelation.currentId());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SUPPLIER_WRITE')") // suppliers create
    public ApiResponse<SupplierResponse> create(@Valid @RequestBody SupplierCreateRequest request) {
        return ApiResponse.of(supplierService.create(request), RequestCorrelation.currentId());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SUPPLIER_READ')")
    public ApiResponse<SupplierResponse> get(@PathVariable UUID id) {
        return ApiResponse.of(supplierService.get(id), RequestCorrelation.currentId());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('SUPPLIER_WRITE')")
    public ApiResponse<SupplierResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody SupplierUpdateRequest request) {
        return ApiResponse.of(supplierService.update(id, request), RequestCorrelation.currentId());
    }
}
