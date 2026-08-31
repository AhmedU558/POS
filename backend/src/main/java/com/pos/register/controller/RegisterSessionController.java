package com.pos.register.controller;

import com.pos.common.config.RequestCorrelation;
import com.pos.common.response.ApiResponse;
import com.pos.register.dto.RegisterSessionOpenRequest;
import com.pos.register.dto.RegisterSessionResponse;
import com.pos.register.service.RegisterSessionService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class RegisterSessionController {

    private final RegisterSessionService registerSessionService;

    public RegisterSessionController(RegisterSessionService registerSessionService) {
        this.registerSessionService = registerSessionService;
    }

    @PostMapping("/registers/{id}/sessions/open")
    @PreAuthorize("hasAuthority('REGISTER_OPEN')") // pos open register
    public ApiResponse<RegisterSessionResponse> open(
            @PathVariable UUID id,
            @Valid @RequestBody RegisterSessionOpenRequest request) {
        return ApiResponse.of(registerSessionService.open(id, request), RequestCorrelation.currentId());
    }

    @GetMapping("/register-sessions/{id}")
    @PreAuthorize("hasAnyAuthority('REGISTER_READ', 'REGISTER_OPEN')")
    public ApiResponse<RegisterSessionResponse> get(@PathVariable UUID id) {
        return ApiResponse.of(registerSessionService.get(id), RequestCorrelation.currentId());
    }
}
