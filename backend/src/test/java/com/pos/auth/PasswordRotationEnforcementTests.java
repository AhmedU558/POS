package com.pos.auth;

import com.pos.AbstractIntegrationTest;
import com.pos.auth.security.PasswordRotationFilter;
import com.pos.users.domain.User;
import com.pos.users.repository.UserRepository;
import com.pos.users.repository.RoleRepository;
import com.pos.users.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that a flagged account is confined to the operations that clear the flag.
 *
 * <p>This is the half that makes the rotation requirement real. Without it the flag is metadata
 * and the bootstrap administrator holds a fully usable super-administrator credential.
 */
class PasswordRotationEnforcementTests extends AbstractIntegrationTest {

    /*
     * Usernames are unique per test and accounts are never deleted.
     *
     * A successful change writes an audit row naming the acting user, and audit_logs has no
     * ON DELETE clause, so that account can never be removed (ADR-016). Deleting fixtures would
     * fail for the very reason the audit trail exists.
     */
    private String flaggedName;
    private String unflaggedName;
    private static final String PASSWORD = "current-password-value";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

        @BeforeEach
    void createUsers() {
        flaggedName = "flagged." + java.util.UUID.randomUUID();
        unflaggedName = "unflagged." + java.util.UUID.randomUUID();

        Role adminRole = roleRepository.findByName("Super Administrator").orElseThrow();

        User flaggedUser = new User(flaggedName, passwordEncoder.encode(PASSWORD), "Flagged", "User");
        flaggedUser.assignRole(adminRole);
        flaggedUser.requirePasswordChange();
        userRepository.saveAndFlush(flaggedUser);

        User unflaggedUser = new User(unflaggedName, passwordEncoder.encode(PASSWORD), "Unflagged", "User");
        unflaggedUser.assignRole(adminRole);
        userRepository.saveAndFlush(unflaggedUser);
    }

    @Test
    void aFlaggedAccountIsBlockedFromProtectedRoutes() throws Exception {
        mockMvc.perform(get("/api/v1/made-up-endpoint-for-test").with(user(flaggedName)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_CHANGE_REQUIRED"))
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
    }

    @Test
    void theBlockIsDistinguishableFromAPlainPermissionDenial() throws Exception {
        // UI/UX section 28 requires a denial to explain itself. A generic ACCESS_DENIED would
        // leave the client unable to route the user to the screen that resolves the block.
        mockMvc.perform(get("/api/v1/sales").with(user(flaggedName)))
                .andExpect(jsonPath("$.error.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void aFlaggedAccountStillReachesEveryAllowListedRoute() throws Exception {
        // These three exist so a blocked session can fix itself, end cleanly, and render who is
        // signed in. /auth/logout and /auth/me have no handler until Story 1.6.
        //
        // Asserted as "not blocked" rather than "404": once those handlers land, an exact-status
        // assertion would start failing for a reason unrelated to this guarantee, and the cheapest
        // repair would be to loosen it -- quietly retiring the allow-list coverage for two of the
        // three routes. This form survives the handlers arriving.
        assertNotBlocked(get("/api/v1/auth/me").with(user(flaggedName)));
        assertNotBlocked(post("/api/v1/auth/logout").with(user(flaggedName)));

        mockMvc.perform(
                        post("/api/v1/auth/change-password")
                                .with(user(flaggedName))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"currentPassword\":\"" + PASSWORD
                                                + "\",\"newPassword\":\"a-brand-new-password\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void anUnflaggedAccountIsUnaffected() throws Exception {
        // Asserted as "not blocked" rather than 404: /products gains a handler in Phase 2, and an
        // exact-status assertion would then fail for a reason having nothing to do with this
        // filter, inviting someone to weaken it.
        assertNotBlocked(get("/api/v1/made-up-endpoint-for-test").with(user(unflaggedName)));
    }

    @Test
    void anAnonymousRequestToAPublicRouteIsUnaffected() throws Exception {
        mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk());
    }

    @Test
    void theBlockLiftsOnceTheRequirementIsSatisfied() throws Exception {
        mockMvc.perform(get("/api/v1/made-up-endpoint-for-test").with(user(flaggedName)))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        post("/api/v1/auth/change-password")
                                .with(user(flaggedName))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"currentPassword\":\"" + PASSWORD
                                                + "\",\"newPassword\":\"a-brand-new-password\"}"))
                .andExpect(status().isNoContent());

        // Read fresh from the database on the very next request, not at token expiry (ADR-013).
        mockMvc.perform(get("/api/v1/made-up-endpoint-for-test").with(user(flaggedName)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aClientSuppliedRotationFlagChangesNothing() throws Exception {
        // The injected fields must be the reason this fails to clear the flag -- so the request is
        // otherwise well-formed and the CORRECT current password is supplied. An earlier version
        // sent a wrong password, which made the service throw at the credential check before any
        // flag-bearing code ran: the injected fields were inert and deleting them changed nothing.
        //
        // The new password is deliberately too short, so the request travels past the credential
        // check and into policy, and the only thing that could clear the flag is the injection.
        mockMvc.perform(
                        post("/api/v1/auth/change-password")
                                .with(user(flaggedName))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"short\","
                                                + "\"isPasswordChangeRequired\":false,"
                                                + "\"passwordChangeRequired\":false}"))
                .andExpect(status().isUnprocessableEntity());

        assertThat(userRepository.findByUsername(flaggedName).orElseThrow().isPasswordChangeRequired())
                .isTrue();

        mockMvc.perform(get("/api/v1/made-up-endpoint-for-test").with(user(flaggedName)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void theSubjectComesFromThePrincipalAndNeverFromTheRequestBody() throws Exception {
        // A body naming somebody else must not reach that account. Without this, the only thing
        // standing between a caller and another user's password is that ChangePasswordRequest
        // happens to have two components today.
        mockMvc.perform(
                        post("/api/v1/auth/change-password")
                                .with(user(flaggedName))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"currentPassword\":\"" + PASSWORD + "\","
                                                + "\"newPassword\":\"a-brand-new-password\","
                                                + "\"username\":\"" + unflaggedName + "\","
                                                + "\"userId\":\"" + unflaggedName + "\"}"))
                .andExpect(status().isNoContent());

        // The caller's own account changed...
        User caller = userRepository.findByUsername(flaggedName).orElseThrow();
        assertThat(caller.isPasswordChangeRequired()).isFalse();
        assertThat(passwordEncoder.matches("a-brand-new-password", caller.getPasswordHash()))
                .isTrue();

        // ...and the account named in the body did not.
        User named = userRepository.findByUsername(unflaggedName).orElseThrow();
        assertThat(passwordEncoder.matches(PASSWORD, named.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("a-brand-new-password", named.getPasswordHash()))
                .isFalse();
    }

    @Test
    void theAllowListMatchesExactlyAndNotByPrefix() throws Exception {
        // A prefix match on /api/v1/auth/ would open every authentication endpoint added later,
        // long after anyone remembers this filter exists.
        mockMvc.perform(get("/api/v1/auth/me/extra").with(user(flaggedName)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void onlyTheThreeApprovedRoutesAreAllowListed() throws Exception {
        // Any other /auth path must still be blocked for a flagged account.
        for (String path : new String[] {"/api/v1/auth/refresh", "/api/v1/auth/sessions"}) {
            mockMvc.perform(get(path).with(user(flaggedName)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("PASSWORD_CHANGE_REQUIRED"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void theAllowListContainsExactlyTheThreeApprovedRoutesAndNothingElse() throws Exception {
        // Probing a couple of paths cannot prove a negative: a fourth entry -- say
        // "/api/v1/auth/refresh-token" -- would slip past every behavioural test above. The
        // approved list is exactly three (AMD-002 section 3), so assert exactly three.
        java.lang.reflect.Field field =
                PasswordRotationFilter.class.getDeclaredField("ALLOW_LIST");
        field.setAccessible(true);
        Set<String> allowList = (Set<String>) field.get(null);

        assertThat(allowList)
                .containsExactlyInAnyOrder(
                        "/api/v1/auth/change-password",
                        "/api/v1/auth/logout",
                        "/api/v1/auth/me");
    }

    @Test
    void anAuthenticatedPrincipalWithNoAccountIsBlockedRatherThanWavedThrough() throws Exception {
        // Fail closed. If the principal cannot be resolved to a row, the filter cannot confirm the
        // account may proceed. Failing open here would silently disable enforcement system-wide
        // the moment Story 1.4 puts anything but the username in the token subject.
        mockMvc.perform(get("/api/v1/made-up-endpoint-for-test").with(user("no.such.account")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void theAllowListStillMatchesWhenDeployedUnderAContextPath() throws Exception {
        // getRequestURI() includes the context path; the allow-list entries do not. Without
        // stripping it, deploying under /pos would block a flagged account from the very endpoint
        // that clears its own flag -- a silent, total lockout caused by one line of config.
        assertNotBlocked(
                get("/pos/api/v1/auth/me").contextPath("/pos").with(user(flaggedName)));
    }

    @Test
    void aContextPathDoesNotSmuggleAProtectedRouteThroughTheAllowList() throws Exception {
        mockMvc.perform(get("/pos/api/v1/made-up-endpoint-for-test").contextPath("/pos").with(user(flaggedName)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    /** Asserts the rotation filter let the request past, whatever the eventual handler does. */
    private void assertNotBlocked(org.springframework.test.web.servlet.RequestBuilder request)
            throws Exception {
        var response = mockMvc.perform(request).andReturn().getResponse();

        assertThat(response.getStatus()).isNotEqualTo(403);
        assertThat(response.getContentAsString()).doesNotContain("PASSWORD_CHANGE_REQUIRED");
    }
}