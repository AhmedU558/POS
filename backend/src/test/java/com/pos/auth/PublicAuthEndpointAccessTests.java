package com.pos.auth;

import com.pos.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The permissive half of the {@code SecurityConfig} narrowing.
 *
 * <p>Goal B replaced a blanket {@code /api/v1/auth/**} permitAll with an explicit three-path list
 * (REST API Specification §4.2). Every existing test covered only the restrictive half — that
 * change-password now requires authentication. Nothing covered the half that keeps login reachable,
 * so deleting the enumeration would have shipped an authentication flow no anonymous caller could
 * reach, with a completely green build.
 *
 * <p>The handlers arrive in Story 1.4; 404 is expected today. What is asserted is only that the
 * request is not turned away by access control, which stays true once the handlers exist.
 */
class PublicAuthEndpointAccessTests extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/api/v1/auth/login",
                "/api/v1/auth/refresh",
                "/api/v1/auth/forgot-password",
                "/api/v1/auth/reset-password"
            })
    void thePublicAuthenticationEndpointsAreReachableAnonymously(String path) throws Exception {
        int status =
                mockMvc.perform(
                                post(path)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}"))
                        .andReturn()
                        .getResponse()
                        .getStatus();

        // Deliberately not an exact status: these have no handler until Story 1.4. The claim is
        // that access control let the request past, which must remain true afterwards.
        assertThat(status).isNotIn(401, 403);
    }

    @Test
    void everyOtherAuthenticationRouteStillRequiresAuthentication() throws Exception {
        // The counterpart: narrowing means anything NOT enumerated is now protected. /auth/refresh,
        // /auth/logout and /auth/me were public under the old wildcard.
        for (String path :
                new String[] {
                    "/api/v1/auth/logout", "/api/v1/auth/me",
                    "/api/v1/auth/change-password"
                }) {
            int status =
                    mockMvc.perform(
                                    post(path)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content("{}"))
                            .andReturn()
                            .getResponse()
                            .getStatus();

            assertThat(status).as("anonymous %s", path).isEqualTo(401);
        }
    }
}
