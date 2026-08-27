package com.pos.organization.controller;

import com.pos.auth.security.CustomUserDetails;
import com.pos.common.response.ApiResponse;
import com.pos.organization.dto.StoreRequest;
import com.pos.organization.dto.StoreResponse;
import com.pos.organization.dto.StoreStatusRequest;
import com.pos.organization.service.StoreService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api/v1/stores")
public class StoreController {

    private final StoreService storeService;

    public StoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('STORE_READ')")
    public ApiResponse<List<StoreResponse>> listStores(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.of(storeService.listStores(userDetails.getId()), com.pos.common.config.RequestCorrelation.currentId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('STORE_WRITE')")
    public ApiResponse<StoreResponse> createStore(@Valid @RequestBody StoreRequest request) {
        return ApiResponse.of(storeService.createStore(request), com.pos.common.config.RequestCorrelation.currentId());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('STORE_READ') and @storeScopeEvaluator.canAccess(#id)")
    public ApiResponse<StoreResponse> getStore(@PathVariable UUID id) {
        return ApiResponse.of(storeService.getStore(id), com.pos.common.config.RequestCorrelation.currentId());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('STORE_WRITE') and @storeScopeEvaluator.canAccess(#id)")
    public ApiResponse<StoreResponse> updateStore(
            @PathVariable UUID id,
            @Valid @RequestBody StoreRequest request) {
        return ApiResponse.of(storeService.updateStore(id, request), com.pos.common.config.RequestCorrelation.currentId());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('STORE_WRITE') and @storeScopeEvaluator.canAccess(#id)")
    public ApiResponse<StoreResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody StoreStatusRequest request) {
        return ApiResponse.of(storeService.updateStatus(id, request), com.pos.common.config.RequestCorrelation.currentId());
    }
}
