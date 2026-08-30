package com.pos.suppliers.controller;

import com.pos.common.config.RequestCorrelation;
import com.pos.common.response.ApiResponse;
import com.pos.accounts.dto.SupplierStatementLineResponse;
import com.pos.accounts.service.SupplierStatementService;
import com.pos.suppliers.dto.SupplierCreateRequest;
import com.pos.suppliers.dto.SupplierProductResponse;
import com.pos.suppliers.dto.SupplierProductsReplaceRequest;
import com.pos.suppliers.dto.SupplierResponse;
import com.pos.suppliers.dto.SupplierUpdateRequest;
import com.pos.suppliers.service.SupplierProductService;
import com.pos.suppliers.service.SupplierService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/suppliers")
public class SupplierController {

    private final SupplierService supplierService;
    private final SupplierProductService supplierProductService;
    private final SupplierStatementService supplierStatementService;

    public SupplierController(
            SupplierService supplierService,
            SupplierProductService supplierProductService,
            SupplierStatementService supplierStatementService) {
        this.supplierService = supplierService;
        this.supplierProductService = supplierProductService;
        this.supplierStatementService = supplierStatementService;
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

    @GetMapping("/{id}/products")
    @PreAuthorize("hasAuthority('SUPPLIER_READ')") // supplier products list
    public ApiResponse<List<SupplierProductResponse>> listProducts(@PathVariable UUID id) {
        return ApiResponse.of(supplierProductService.list(id), RequestCorrelation.currentId());
    }

    @PutMapping("/{id}/products")
    @PreAuthorize("hasAuthority('SUPPLIER_WRITE')") // supplier products replace
    public ApiResponse<List<SupplierProductResponse>> replaceProducts(
            @PathVariable UUID id,
            @Valid @RequestBody SupplierProductsReplaceRequest request) {
        return ApiResponse.of(supplierProductService.replace(id, request), RequestCorrelation.currentId());
    }

    @GetMapping("/{id}/statement")
    @PreAuthorize("hasAuthority('AP_READ')") // supplier statement
    public ApiResponse<Page<SupplierStatementLineResponse>> statement(@PathVariable UUID id, Pageable pageable) {
        return ApiResponse.of(supplierStatementService.statement(id, pageable), RequestCorrelation.currentId());
    }
}
