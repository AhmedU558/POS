package com.pos.register.controller;

import com.pos.common.config.RequestCorrelation;
import com.pos.common.response.ApiResponse;
import com.pos.register.dto.CashMovementRequest;
import com.pos.register.dto.CashMovementResponse;
import com.pos.register.dto.RegisterClosingReportResponse;
import com.pos.register.dto.RegisterSessionCloseRequest;
import com.pos.register.dto.RegisterSessionOpenRequest;
import com.pos.register.dto.RegisterSessionResponse;
import com.pos.register.dto.RegisterSessionSummaryResponse;
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

    /**
     * The caller's own open session, or a null payload when they have none.
     *
     * <p>Mapped above {@code /register-sessions/{id}} deliberately: "current" is a literal path
     * segment and must not be parsed as a session identifier.
     */
    @GetMapping("/register-sessions/current")
    @PreAuthorize("hasAnyAuthority('REGISTER_READ', 'REGISTER_OPEN', 'SALE_CREATE')")
    public ApiResponse<RegisterSessionResponse> current() {
        return ApiResponse.of(registerSessionService.currentForCashier(), RequestCorrelation.currentId());
    }

    @GetMapping("/register-sessions/{id}/summary")
    @PreAuthorize("hasAnyAuthority('REGISTER_READ', 'REGISTER_OPEN')")
    public ApiResponse<RegisterSessionSummaryResponse> summary(@PathVariable UUID id) {
        return ApiResponse.of(registerSessionService.summary(id), RequestCorrelation.currentId());
    }

    @GetMapping("/register-sessions/{id}/closing-report")
    @PreAuthorize("hasAnyAuthority('REGISTER_READ', 'REGISTER_OPEN', 'REGISTER_CLOSE')")
    public ApiResponse<RegisterClosingReportResponse> closingReport(@PathVariable UUID id) {
        return ApiResponse.of(registerSessionService.closingReport(id), RequestCorrelation.currentId());
    }

    @PostMapping("/register-sessions/{id}/close")
    @PreAuthorize("hasAuthority('REGISTER_CLOSE')")
    public ApiResponse<RegisterClosingReportResponse> close(
            @PathVariable UUID id,
            @Valid @RequestBody RegisterSessionCloseRequest request) {
        return ApiResponse.of(registerSessionService.close(id, request), RequestCorrelation.currentId());
    }

    @GetMapping("/register-sessions/{id}")
    @PreAuthorize("hasAnyAuthority('REGISTER_READ', 'REGISTER_OPEN')")
    public ApiResponse<RegisterSessionResponse> get(@PathVariable UUID id) {
        return ApiResponse.of(registerSessionService.get(id), RequestCorrelation.currentId());
    }

    @PostMapping("/register-sessions/{id}/cash-in")
    @PreAuthorize("hasAuthority('REGISTER_CASH')")
    public ApiResponse<CashMovementResponse> cashIn(
            @PathVariable UUID id,
            @Valid @RequestBody CashMovementRequest request) {
        return ApiResponse.of(registerSessionService.cashIn(id, request), RequestCorrelation.currentId());
    }

    @PostMapping("/register-sessions/{id}/cash-out")
    @PreAuthorize("hasAuthority('REGISTER_CASH')")
    public ApiResponse<CashMovementResponse> cashOut(
            @PathVariable UUID id,
            @Valid @RequestBody CashMovementRequest request) {
        return ApiResponse.of(registerSessionService.cashOut(id, request), RequestCorrelation.currentId());
    }
}
