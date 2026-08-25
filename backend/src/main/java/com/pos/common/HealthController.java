package com.pos.common;

import com.pos.common.config.RequestCorrelation;
import com.pos.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * API contract health probe.
 *
 * <p>Lives under the versioned base path required by REST API Specification section 2.1 and
 * returns the standard success envelope from section 5.1. Infrastructure liveness and readiness
 * are a separate concern served by Actuator at {@code /actuator/health}; see
 * {@code docs/adr/ADR-009-health-endpoints.md}.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Health", description = "Service availability")
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "Report API availability")
    public ResponseEntity<ApiResponse<Map<String, String>>> healthCheck() {
        return ResponseEntity.ok(
                ApiResponse.of(Map.of("status", "UP"), RequestCorrelation.currentId()));
    }
}
