package com.pos.organization.controller;

import com.pos.common.response.ApiResponse;
import com.pos.organization.dto.TerminalRequest;
import com.pos.organization.dto.TerminalResponse;
import com.pos.organization.service.TerminalService;
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
@RequestMapping("/api/v1/stores/{storeId}/terminals")
public class TerminalController {

    private final TerminalService terminalService;

    public TerminalController(TerminalService terminalService) {
        this.terminalService = terminalService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TERMINAL_READ') and @storeScopeEvaluator.canAccess(#storeId)")
    public ApiResponse<List<TerminalResponse>> listTerminals(@PathVariable UUID storeId) {
        return ApiResponse.of(terminalService.listTerminals(storeId), com.pos.common.config.RequestCorrelation.currentId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TERMINAL_WRITE') and @storeScopeEvaluator.canAccess(#storeId)")
    public ApiResponse<TerminalResponse> createTerminal(
            @PathVariable UUID storeId,
            @Valid @RequestBody TerminalRequest request) {
        return ApiResponse.of(terminalService.createTerminal(storeId, request), com.pos.common.config.RequestCorrelation.currentId());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('TERMINAL_READ') and @storeScopeEvaluator.canAccess(#storeId)")
    public ApiResponse<TerminalResponse> getTerminal(@PathVariable UUID storeId, @PathVariable UUID id) {
        return ApiResponse.of(terminalService.getTerminal(storeId, id), com.pos.common.config.RequestCorrelation.currentId());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('TERMINAL_WRITE') and @storeScopeEvaluator.canAccess(#storeId)")
    public ApiResponse<TerminalResponse> updateTerminal(
            @PathVariable UUID storeId,
            @PathVariable UUID id,
            @Valid @RequestBody TerminalRequest request) {
        return ApiResponse.of(terminalService.updateTerminal(storeId, id, request), com.pos.common.config.RequestCorrelation.currentId());
    }
}
