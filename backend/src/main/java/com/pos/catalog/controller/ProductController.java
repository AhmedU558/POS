package com.pos.catalog.controller;

import com.pos.common.config.RequestCorrelation;

import com.pos.catalog.dto.*;
import com.pos.catalog.service.ProductService;
import com.pos.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    public ApiResponse<Page<ProductResponse>> searchProducts(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID brandId,
            @RequestParam(required = false) Boolean isActive,
            Pageable pageable) {
        return ApiResponse.of(productService.searchProducts(query, categoryId, brandId, isActive, pageable), RequestCorrelation.currentId());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public ApiResponse<ProductResponse> createProduct(@Valid @RequestBody ProductCreateRequest request) {
        return ApiResponse.of(productService.createProduct(request), RequestCorrelation.currentId());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    public ApiResponse<ProductResponse> getProduct(@PathVariable UUID id) {
        return ApiResponse.of(productService.getProduct(id), RequestCorrelation.currentId());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public ApiResponse<ProductResponse> updateProduct(@PathVariable UUID id, @Valid @RequestBody ProductUpdateRequest request) {
        return ApiResponse.of(productService.updateProduct(id, request), RequestCorrelation.currentId());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public ApiResponse<Void> updateStatus(@PathVariable UUID id, @Valid @RequestBody ProductStatusUpdateRequest request) {
        productService.updateStatus(id, request);
        return ApiResponse.of((Void) null, RequestCorrelation.currentId());
    }

    @GetMapping("/{id}/barcodes")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    public ApiResponse<List<BarcodeResponse>> getBarcodes(@PathVariable UUID id) {
        return ApiResponse.of(productService.getBarcodes(id), RequestCorrelation.currentId());
    }

    @PostMapping("/{id}/barcodes")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public ApiResponse<BarcodeResponse> addBarcode(@PathVariable UUID id, @Valid @RequestBody BarcodeCreateRequest request) {
        return ApiResponse.of(productService.addBarcode(id, request), RequestCorrelation.currentId());
    }

    @DeleteMapping("/{id}/barcodes/{barcodeId}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public ApiResponse<Void> removeBarcode(@PathVariable UUID id, @PathVariable UUID barcodeId) {
        productService.removeBarcode(id, barcodeId);
        return ApiResponse.of((Void) null, RequestCorrelation.currentId());
    }

    @GetMapping("/{id}/prices")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    public ApiResponse<List<PriceResponse>> getPrices(@PathVariable UUID id) {
        return ApiResponse.of(productService.getPrices(id), RequestCorrelation.currentId());
    }

    @PostMapping("/{id}/prices")
    @PreAuthorize("hasAuthority('PRODUCT_PRICE_WRITE')")
    public ApiResponse<PriceResponse> addPrice(@PathVariable UUID id, @Valid @RequestBody PriceCreateRequest request) {
        return ApiResponse.of(productService.addPrice(id, request), RequestCorrelation.currentId());
    }
}
