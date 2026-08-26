package com.pos.common.security;

import com.pos.AbstractIntegrationTest;
import com.pos.users.domain.User;
import com.pos.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security foundation tests.
 *
 * <p>Covers the default-deny posture (SAD section 15), the browser access policy required for the
 * separate-origin frontend (UI/UX section 4), and the requirement that security rejections use the
 * standard error envelope with documented codes (API spec sections 5.2 and 28).
 */
class SecurityFoundationTests extends AbstractIntegrationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:3000";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void protectedEndpointsRejectAnonymousCallersWithTheStandardEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.error.message").isNotEmpty())
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty())
                .andExpect(jsonPath("$.meta.timestamp").isNotEmpty());
    }

    @Test
    void unauthorisedResponsesNeverLeakAStackTraceOrInternalDetail() throws Exception {
        String body =
                mockMvc.perform(get("/api/v1/sales"))
                        .andExpect(status().isUnauthorized())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertThat(body).doesNotContain("Exception").doesNotContain("com.pos.");
    }

    @Test
    void authenticatedRequestForAnUnmappedPathReturnsTheNotFoundCode() throws Exception {
        // A persisted account, not @WithMockUser's default "user". The rotation filter added in
        // Goal B fails closed on a principal it cannot resolve to a row, so a phantom principal
        // now gets 403 -- correctly, but it would mask what this test is actually about.
        String username = "foundation." + java.util.UUID.randomUUID();
        userRepository.saveAndFlush(
                new User(username, passwordEncoder.encode("irrelevant-value"), "Foundation", "User"));

        mockMvc.perform(get("/api/v1/does-not-exist").with(user(username)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
    }

    @Test
    void healthProbesArePublic() throws Exception {
        mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk());

        // The Actuator probe aggregates dependency health, so its status code reflects whether
        // Redis and the database are reachable. What matters here is only that the security
        // chain lets an anonymous caller through: an orchestrator must be able to read the
        // probe without credentials.
        int actuatorStatus =
                mockMvc.perform(get("/actuator/health")).andReturn().getResponse().getStatus();

        assertThat(actuatorStatus).isNotIn(401, 403);
    }

    @Test
    void preflightFromAnAllowedOriginIsAccepted() throws Exception {
        mockMvc.perform(
                        options("/api/v1/products")
                                .header("Origin", ALLOWED_ORIGIN)
                                .header("Access-Control-Request-Method", "POST")
                                .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN));
    }

    @Test
    void preflightFromAnUnknownOriginIsRejected() throws Exception {
        mockMvc.perform(
                        options("/api/v1/products")
                                .header("Origin", "https://attacker.example")
                                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void corsPolicyDoesNotAllowCredentials() throws Exception {
        // The approved contract authenticates with a bearer header, not cookies. Allowing
        // credentials would widen the browser attack surface for no contractual benefit.
        mockMvc.perform(
                        options("/api/v1/products")
                                .header("Origin", ALLOWED_ORIGIN)
                                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void apiIsStatelessAndIssuesNoSessionCookie() throws Exception {
        var response =
                mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk()).andReturn().getResponse();

        assertThat(response.getCookies()).isEmpty();
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }
}
