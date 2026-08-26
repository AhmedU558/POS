package com.pos.auth;

import com.pos.AbstractIntegrationTest;
import com.pos.auth.service.PasswordChangeService;
import com.pos.users.domain.User;
import com.pos.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The change-password contract from AMD-002: every status and every error code.
 *
 * <p>Driven with mock principals. JWT and login are Story 1.4, so nothing populates a
 * {@code SecurityContext} in production yet — the endpoint reads
 * {@code Authentication.getName()}, which is exactly what a JWT filter will later supply.
 */
class ChangePasswordApiTests extends AbstractIntegrationTest {

    /*
     * Usernames are unique per test and accounts are never deleted.
     *
     * A successful change writes an audit row naming the acting user, and audit_logs has no
     * ON DELETE clause, so that account can never be removed (ADR-016). Deleting fixtures would
     * fail for the very reason the audit trail exists.
     */
    private String username;
    private static final String CURRENT = "current-password-value";
    private static final String ENDPOINT = "/api/v1/auth/change-password";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void createFlaggedUser() {
        username = "rotating." + java.util.UUID.randomUUID();
        User user = new User(username, passwordEncoder.encode(CURRENT), "Rotating", "User");
        user.requirePasswordChange();
        userRepository.saveAndFlush(user);
    }

    @Test
    void aValidChangeReturnsNoContentAndClearsTheRequirement() throws Exception {
        change(CURRENT, "a-brand-new-password").andExpect(status().isNoContent());

        User reloaded = userRepository.findByUsername(username).orElseThrow();
        assertThat(reloaded.isPasswordChangeRequired()).isFalse();
        assertThat(passwordEncoder.matches("a-brand-new-password", reloaded.getPasswordHash()))
                .isTrue();
    }

    @Test
    void theResponseCarriesNoBodyAtAll() throws Exception {
        // Returning the user or a token would invite the response to be read as an
        // authentication result (AMD-002 section 2).
        String body =
                change(CURRENT, "a-brand-new-password")
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertThat(body).isEmpty();
    }

    @Test
    void theReplacementIsStoredOnlyAsAHash() throws Exception {
        change(CURRENT, "a-brand-new-password").andExpect(status().isNoContent());

        String stored =
                jdbcTemplate.queryForObject(
                        "SELECT password_hash FROM users WHERE username = ?", String.class, username);

        assertThat(stored).isNotEqualTo("a-brand-new-password").startsWith("$2");
    }

    @Test
    void aWrongCurrentPasswordIsRejectedWithoutRevealingWhy() throws Exception {
        change("not-the-current-password", "a-brand-new-password")
                .andExpect(status().isUnauthorized())
                // Deliberately the same code as "no credentials": a distinct one would make this
                // endpoint a password oracle for a hijacked session.
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));

        assertAccountUntouched();
    }

    @Test
    void aReplacementShorterThanThePolicyFloorIsRejected() throws Exception {
        change(CURRENT, "short")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.error.message").value(
                        org.hamcrest.Matchers.containsString("12")));

        assertAccountUntouched();
    }

    @Test
    void aReplacementOneCharacterShortOfThePolicyFloorIsRejected() throws Exception {
        // The floor itself was pinned by the error message, but the comparison operator was not:
        // "< 12" and "<= 12" behave identically against a 5-character probe and a 20-character
        // success. These two tests sit either side of the boundary.
        change(CURRENT, "12345678901")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));

        assertAccountUntouched();
    }

    @Test
    void aReplacementExactlyAtThePolicyFloorIsAccepted() throws Exception {
        change(CURRENT, "123456789012").andExpect(status().isNoContent());

        assertThat(userRepository.findByUsername(username).orElseThrow().isPasswordChangeRequired())
                .isFalse();
    }

    @Test
    void thePolicyFloorCountsCharactersRatherThanUtf16Units() throws Exception {
        // Six emoji are twelve UTF-16 code units. String.length() would call that a
        // twelve-character password; a person would call it six.
        String emoji = new String(Character.toChars(0x1F510));

        change(CURRENT, emoji.repeat(6))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));

        assertAccountUntouched();
    }

    @Test
    void aReplacementLongerThanBcryptCanHashIsRejectedRatherThanTruncated() throws Exception {
        // BCrypt hashes the first 72 bytes and silently discards the rest, so accepting this
        // would protect a long passphrase with a fraction of itself while looking stronger.
        change(CURRENT, "a".repeat(73))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));

        assertAccountUntouched();
    }

    @Test
    void aReplacementExactlyAtTheBcryptBoundIsAccepted() throws Exception {
        change(CURRENT, "a".repeat(72)).andExpect(status().isNoContent());
    }

    @Test
    void theBcryptBoundIsMeasuredInBytesNotCharacters() throws Exception {
        // 40 two-byte characters are 80 bytes: comfortably under any character-based limit, and
        // over the one that actually matters.
        String twoByteCharacter = new String(Character.toChars(0x00E9));

        change(CURRENT, twoByteCharacter.repeat(40))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));

        assertAccountUntouched();
    }

    @Test
    void aReplacementIdenticalToTheCurrentPasswordIsRejected() throws Exception {
        // Otherwise the rotation requirement could be satisfied by re-setting the very credential
        // it exists to retire.
        change(CURRENT, CURRENT)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));

        assertAccountUntouched();
    }

    @Test
    void aBlankFieldIsAValidationErrorNotAPolicyFailure() throws Exception {
        // AMD-002 maps a malformed request to 400 and a policy failure to 422; they are different
        // outcomes and the client is expected to tell them apart.
        change("", "a-brand-new-password")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        assertAccountUntouched();
    }

    @Test
    void aMissingBodyIsRejected() throws Exception {
        mockMvc.perform(
                        post(ENDPOINT)
                                .with(user(username))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        assertAccountUntouched();
    }

    @Test
    void anUnauthenticatedCallerCannotReachTheEndpoint() throws Exception {
        // SecurityConfig no longer permits /api/v1/auth/** wholesale.
        mockMvc.perform(
                        post(ENDPOINT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload(CURRENT, "a-brand-new-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));

        assertAccountUntouched();
    }

    @Test
    void aDeactivatedAccountCannotRotateItsWayBackIntoUse() throws Exception {
        User user = userRepository.findByUsername(username).orElseThrow();
        user.setActive(false);
        userRepository.saveAndFlush(user);

        // Reported as a credential failure, not a distinct code: the endpoint must not disclose
        // account status to a caller holding a stolen session either.
        change(CURRENT, "a-brand-new-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));

        assertAccountUntouched();
    }

    @Test
    void aWrongMethodIsAClientErrorRatherThanAServerError() throws Exception {
        // The catch-all @ExceptionHandler(Exception.class) used to answer these with 500
        // INTERNAL_ERROR and an ERROR-level log line. Neither 405 nor 415 is in the approved
        // status set (REST API Specification section 5.2), so both belong at 400.
        mockMvc.perform(get(ENDPOINT).with(user(username)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void anUnsupportedContentTypeIsAClientErrorRatherThanAServerError() throws Exception {
        mockMvc.perform(
                        post(ENDPOINT)
                                .with(user(username))
                                .contentType(MediaType.TEXT_PLAIN)
                                .content(payload(CURRENT, "a-brand-new-password")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void aSuccessfulChangeRecordsAnAuditEventAgainstTheActingUser() throws Exception {
        change(CURRENT, "a-brand-new-password").andExpect(status().isNoContent());

        java.util.UUID userId = userRepository.findByUsername(username).orElseThrow().getId();
        var rows =
                jdbcTemplate.queryForList(
                        "SELECT actor_user_id FROM audit_logs WHERE action = ? AND entity_id = ?",
                        PasswordChangeService.PASSWORD_CHANGED,
                        userId);

        assertThat(rows).hasSize(1);
        // A human actor this time, not SYSTEM: a person performed this.
        assertThat(rows.get(0).get("actor_user_id")).isEqualTo(userId);
    }

    @Test
    void theAuditRowCarriesTheCallersAddressAndUserAgent() throws Exception {
        // Database Design section 20.1 records both "when available". On a user-driven HTTP action
        // they are available, and this is the first audited action with a human at the other end.
        mockMvc.perform(
                        post(ENDPOINT)
                                .with(user(username))
                                .header(HttpHeaders.USER_AGENT, "probe/1.0")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload(CURRENT, "a-brand-new-password")))
                .andExpect(status().isNoContent());

        java.util.UUID userId = userRepository.findByUsername(username).orElseThrow().getId();
        var row =
                jdbcTemplate.queryForMap(
                        "SELECT ip_address, user_agent FROM audit_logs WHERE action = ?"
                                + " AND entity_id = ?",
                        PasswordChangeService.PASSWORD_CHANGED,
                        userId);

        assertThat(row.get("user_agent")).isEqualTo("probe/1.0");
        assertThat(row.get("ip_address")).isNotNull();
    }

    @Test
    void aFailedChangeRecordsNoAuditEvent() throws Exception {
        change("not-the-current-password", "a-brand-new-password")
                .andExpect(status().isUnauthorized());

        java.util.UUID userId = userRepository.findByUsername(username).orElseThrow().getId();
        Long events =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM audit_logs WHERE action = ? AND entity_id = ?",
                        Long.class,
                        PasswordChangeService.PASSWORD_CHANGED,
                        userId);

        assertThat(events).isZero();
    }

    private org.springframework.test.web.servlet.ResultActions change(
            String currentPassword, String newPassword) throws Exception {
        return mockMvc.perform(
                post(ENDPOINT)
                        .with(user(username))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(currentPassword, newPassword)));
    }

    private static String payload(String currentPassword, String newPassword) {
        return "{\"currentPassword\":%s,\"newPassword\":%s}"
                .formatted(quote(currentPassword), quote(newPassword));
    }

    /** JSON-encodes a value so non-ASCII and special characters survive the round trip. */
    private static String quote(String value) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Neither the credential nor the rotation requirement may move on a failed attempt. */
    private void assertAccountUntouched() {
        User reloaded = userRepository.findByUsername(username).orElseThrow();
        assertThat(reloaded.isPasswordChangeRequired()).isTrue();
        assertThat(passwordEncoder.matches(CURRENT, reloaded.getPasswordHash())).isTrue();
    }
}
