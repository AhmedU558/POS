package com.pos.catalog.controller;

import com.pos.catalog.dto.BrandRequest;
import com.pos.catalog.dto.BrandResponse;
import com.pos.catalog.service.BrandService;
import com.pos.common.config.RequestCorrelation;
import com.pos.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/brands")
public class BrandController {
    private final BrandService brandService;

    public BrandController(BrandService brandService) {
        this.brandService = brandService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    public ApiResponse<List<BrandResponse>> listBrands() {
        return ApiResponse.of(brandService.listBrands(), RequestCorrelation.currentId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public ApiResponse<BrandResponse> createBrand(@Valid @RequestBody BrandRequest request) {
        return ApiResponse.of(brandService.createBrand(request), RequestCorrelation.currentId());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public ApiResponse<BrandResponse> updateBrand(
            @PathVariable UUID id,
            @Valid @RequestBody BrandRequest request) {
        return ApiResponse.of(brandService.updateBrand(id, request), RequestCorrelation.currentId());
    }
}
