package com.pos.catalog.controller;

import com.pos.catalog.dto.UnitRequest;
import com.pos.catalog.dto.UnitResponse;
import com.pos.catalog.service.UnitService;
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
@RequestMapping("/api/v1/units")
public class UnitController {
    private final UnitService unitService;

    public UnitController(UnitService unitService) {
        this.unitService = unitService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    public ApiResponse<List<UnitResponse>> listUnits() {
        return ApiResponse.of(unitService.listUnits(), RequestCorrelation.currentId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public ApiResponse<UnitResponse> createUnit(@Valid @RequestBody UnitRequest request) {
        return ApiResponse.of(unitService.createUnit(request), RequestCorrelation.currentId());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public ApiResponse<UnitResponse> updateUnit(
            @PathVariable UUID id,
            @Valid @RequestBody UnitRequest request) {
        return ApiResponse.of(unitService.updateUnit(id, request), RequestCorrelation.currentId());
    }
}
