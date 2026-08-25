package com.pos.common;

import com.pos.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the health endpoint against the API contract: versioned path (spec section 2.1) and
 * standard success envelope (spec section 5.1).
 */
class HealthControllerTests extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void healthIsPublicAndReturnsTheStandardEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty())
                .andExpect(jsonPath("$.meta.timestamp").isNotEmpty());
    }

    @Test
    void unversionedHealthPathIsNoLongerServed() throws Exception {
        // The pre-Phase-0 endpoint sat outside /api/v1. It must not linger as an
        // unauthenticated surface: anything under /api that is not explicitly permitted
        // now requires authentication.
        mockMvc.perform(get("/api/health")).andExpect(status().isUnauthorized());
    }

    @Test
    void responseCarriesGeneratedCorrelationId() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void suppliedCorrelationIdIsPropagatedToHeaderAndEnvelope() throws Exception {
        String suppliedId = "11111111-2222-3333-4444-555555555555";

        mockMvc.perform(get("/api/v1/health").header("X-Request-Id", suppliedId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", suppliedId))
                .andExpect(jsonPath("$.meta.requestId").value(suppliedId));
    }
}
