package com.pos.organization.controller;

import com.pos.common.response.ApiResponse;
import com.pos.organization.dto.RegisterRequest;
import com.pos.organization.dto.RegisterResponse;
import com.pos.organization.service.RegisterService;
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
@RequestMapping("/api/v1/stores/{storeId}/registers")
public class RegisterController {

    private final RegisterService registerService;

    public RegisterController(RegisterService registerService) {
        this.registerService = registerService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('REGISTER_READ') and @storeScopeEvaluator.canAccess(#storeId)")
    public ApiResponse<List<RegisterResponse>> listRegisters(@PathVariable UUID storeId) {
        return ApiResponse.of(registerService.listRegisters(storeId), com.pos.common.config.RequestCorrelation.currentId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('REGISTER_WRITE') and @storeScopeEvaluator.canAccess(#storeId)")
    public ApiResponse<RegisterResponse> createRegister(
            @PathVariable UUID storeId,
            @Valid @RequestBody RegisterRequest request) {
        return ApiResponse.of(registerService.createRegister(storeId, request), com.pos.common.config.RequestCorrelation.currentId());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('REGISTER_READ') and @storeScopeEvaluator.canAccess(#storeId)")
    public ApiResponse<RegisterResponse> getRegister(@PathVariable UUID storeId, @PathVariable UUID id) {
        return ApiResponse.of(registerService.getRegister(storeId, id), com.pos.common.config.RequestCorrelation.currentId());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('REGISTER_WRITE') and @storeScopeEvaluator.canAccess(#storeId)")
    public ApiResponse<RegisterResponse> updateRegister(
            @PathVariable UUID storeId,
            @PathVariable UUID id,
            @Valid @RequestBody RegisterRequest request) {
        return ApiResponse.of(registerService.updateRegister(storeId, id, request), com.pos.common.config.RequestCorrelation.currentId());
    }
}
