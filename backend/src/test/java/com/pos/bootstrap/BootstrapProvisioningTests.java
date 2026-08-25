package com.pos.bootstrap;

import com.pos.AbstractIntegrationTest;
import com.pos.bootstrap.config.BootstrapProperties;
import com.pos.bootstrap.repository.BootstrapCompletionRepository;
import com.pos.bootstrap.service.BootstrapCredentialResolver;
import com.pos.bootstrap.service.FirstAdministratorBootstrap;
import com.pos.users.domain.RoleName;
import com.pos.users.domain.User;
import com.pos.users.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The twelve required provisioning scenarios.
 *
 * <p>Not transactional: bootstrap must genuinely commit for "run it again" to mean anything. State
 * is cleaned between tests instead.
 */
class BootstrapProvisioningTests extends AbstractIntegrationTest {

    private static final String USERNAME = "ada.lovelace";
    private static final String PASSWORD = "correct-horse-battery-staple";

    @Autowired private BootstrapProperties properties;
    @Autowired private BootstrapRunner runner;
    @Autowired private FirstAdministratorBootstrap bootstrap;
    @Autowired private BootstrapCredentialResolver credentialResolver;
    @Autowired private UserRepository userRepository;
    @Autowired private BootstrapCompletionRepository completionRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void configureEnabledBootstrap() {
        reset();
        properties.setEnabled(true);
        properties.setUsername(USERNAME);
        properties.setFirstName("Ada");
        properties.setLastName("Lovelace");
        properties.setEmail("ada@example.test");
        properties.setPassword(PASSWORD);
        properties.setPasswordFile(null);
    }

    @AfterEach
    void reset() {
        // Every field, not just the credential: the properties bean is a shared singleton, and a
        // leaked email would collide with the UNIQUE index the moment another class reuses it.
        properties.setEnabled(false);
        properties.setUsername(null);
        properties.setFirstName(null);
        properties.setLastName(null);
        properties.setEmail(null);
        properties.setPassword(null);
        properties.setPasswordFile(null);
        jdbcTemplate.update("DELETE FROM bootstrap_completions");
        // Scoped to this fixture: an unscoped delete would wipe role assignments for every user
        // in the shared test database.
        jdbcTemplate.update(
                "DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username = ?)",
                USERNAME);
        jdbcTemplate.update("DELETE FROM users WHERE username = ?", USERNAME);
    }

    // 1. Fresh database + bootstrap enabled -> administrator created.
    @Test
    void enabledOnAFreshDatabaseCreatesTheAdministrator() {
        assertThat(runner.runBootstrap()).isTrue();

        User admin = userRepository.findByUsername(USERNAME).orElseThrow();
        assertThat(admin.getFirstName()).isEqualTo("Ada");
        assertThat(admin.isActive()).isTrue();
    }

    // 2. Fresh database + bootstrap disabled -> no administrator created.
    @Test
    void disabledCreatesNothingEvenWithCredentialsPresent() {
        properties.setEnabled(false);

        assertThat(runner.runBootstrap()).isFalse();

        assertThat(userRepository.findByUsername(USERNAME)).isEmpty();
        assertThat(completionRepository.count()).isZero();
    }

    // 3. Bootstrap enabled without credentials -> fails safely.
    @Test
    void enabledWithoutAnyCredentialFailsClosed() {
        properties.setPassword(null);
        properties.setPasswordFile(null);

        assertThatThrownBy(() -> bootstrap.bootstrapIfEnabled())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("administrator password");

        assertThat(userRepository.findByUsername(USERNAME)).isEmpty();
        assertThat(completionRepository.count()).isZero();
    }

    @Test
    void aBlankCredentialCountsAsAbsent() {
        properties.setPassword("   ");

        assertThatThrownBy(() -> bootstrap.bootstrapIfEnabled())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void configurationValidationRejectsAnEnabledButUnnamedAdministrator() {
        BootstrapProperties incomplete = new BootstrapProperties();
        incomplete.setEnabled(true);
        incomplete.setPassword(PASSWORD);

        assertThatThrownBy(incomplete::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.bootstrap.username");
    }

    @Test
    void aMountedSecretFileIsPreferredOverTheEnvironmentValue(@org.junit.jupiter.api.io.TempDir Path dir)
            throws IOException {
        Path secret = dir.resolve("admin-password");
        Files.writeString(secret, "from-the-mounted-file\n");
        properties.setPasswordFile(secret.toString());
        properties.setPassword("from-the-environment");

        // Trailing newline stripped: near-universal in mounted secrets.
        assertThat(credentialResolver.resolve()).isEqualTo("from-the-mounted-file");
    }

    @Test
    void anUnreadableSecretFileNeverFallsBackToTheEnvironment() {
        // Silently falling back would mean a deployment that believed it was reading a secret file
        // was quietly using something else.
        properties.setPasswordFile("/nonexistent/path/admin-password");
        properties.setPassword("from-the-environment");

        // Asserts the behaviour -- it refuses rather than silently using the environment value --
        // and that the message names the offending path, not one particular phrasing.
        assertThatThrownBy(() -> credentialResolver.resolve())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("/nonexistent/path/admin-password")
                .hasMessageNotContaining("from-the-environment");
    }

    @Test
    void anEmptySecretFileIsRejectedRatherThanProvisioningAnEmptyPassword(
            @org.junit.jupiter.api.io.TempDir Path dir) throws IOException {
        // A zero-byte secret mount is a common Kubernetes misconfiguration. Without this guard the
        // most privileged account in the system would be created with encode("").
        Path secret = dir.resolve("empty-password");
        Files.writeString(secret, "");
        properties.setPasswordFile(secret.toString());
        properties.setPassword("from-the-environment");

        assertThatThrownBy(() -> credentialResolver.resolve())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is empty");
    }

    @Test
    void aWhitespaceOnlySecretFileIsAlsoRejected(@org.junit.jupiter.api.io.TempDir Path dir)
            throws IOException {
        Path secret = dir.resolve("blank-password");
        Files.writeString(secret, "   \n\t\n");
        properties.setPasswordFile(secret.toString());

        assertThatThrownBy(() -> credentialResolver.resolve())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is empty");
    }

    @Test
    void aSecretFileShorterThanThePolicyFloorIsRejected(@org.junit.jupiter.api.io.TempDir Path dir)
            throws IOException {
        Path secret = dir.resolve("short-password");
        Files.writeString(secret, "short\n");
        properties.setPasswordFile(secret.toString());

        assertThatThrownBy(() -> credentialResolver.resolve())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("12");
    }

    @Test
    void enablingBootstrapAfterStartupStillRevalidatesTheConfiguration() {
        // @PostConstruct validation only covers configuration present at context refresh. The flag
        // can be switched on afterwards, and without re-validation a null username would reach the
        // insert and surface as a NOT NULL violation the runner cannot recognise.
        properties.setUsername(null);

        assertThatThrownBy(() -> bootstrap.bootstrapIfEnabled())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.bootstrap.username");
    }

    @Test
    void theResolverItselfRefusesWhenNoCredentialIsConfigured() {
        // Guarded separately from BootstrapProperties.validate(), which now runs first inside
        // bootstrapIfEnabled(). Without this, the resolver could grow a default fallback and no
        // test would notice, because validate() would keep failing first.
        properties.setPassword(null);
        properties.setPasswordFile(null);

        assertThatThrownBy(() -> credentialResolver.resolve())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no administrator password was supplied");
    }

    @Test
    void aPasswordBelowThePolicyFloorIsRejectedAtConfigurationTime() {
        BootstrapProperties weak = new BootstrapProperties();
        weak.setEnabled(true);
        weak.setUsername("weak.admin");
        weak.setFirstName("Weak");
        weak.setLastName("Admin");
        weak.setPassword("short");

        assertThatThrownBy(weak::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 12 characters");
    }

    @Test
    void anOverLongUsernameIsRejectedBeforeItBecomesAnUnrecognisableViolation() {
        // Otherwise the marker insert fails as value-too-long, which the runner cannot tell apart
        // from a genuine failure -- crash-looping every replica with a misleading cause.
        BootstrapProperties tooLong = new BootstrapProperties();
        tooLong.setEnabled(true);
        tooLong.setUsername("x".repeat(101));
        tooLong.setFirstName("Too");
        tooLong.setLastName("Long");
        tooLong.setPassword(PASSWORD);

        assertThatThrownBy(tooLong::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at most 100 characters");
    }

    // 4. Bootstrap succeeds -> completion marker exists.
    @Test
    void successWritesExactlyOneCompletionMarker() {
        runner.runBootstrap();

        assertThat(completionRepository.count()).isEqualTo(1);
        assertThat(completionRepository.findFirstBy().orElseThrow().getAdministratorUsername())
                .isEqualTo(USERNAME);
    }

    // 5. Restart after successful bootstrap -> no second administrator.
    // 12. A completed bootstrap cannot be re-enabled through ordinary restart.
    @Test
    void aSecondRunWithConfigurationStillEnabledCreatesNothingFurther() {
        runner.runBootstrap();

        assertThat(runner.runBootstrap()).isFalse();

        assertThat(administratorCount()).isEqualTo(1);
        assertThat(completionRepository.count()).isEqualTo(1);
    }

    // 6. Delete the administrator -> restart still does NOT recreate it.
    @Test
    void deletingTheAdministratorDoesNotReArmBootstrap() {
        runner.runBootstrap();
        jdbcTemplate.update("DELETE FROM user_roles");
        jdbcTemplate.update("DELETE FROM users WHERE username = ?", USERNAME);
        assertThat(userRepository.findByUsername(USERNAME)).isEmpty();

        // The resurrection vector: a guard of "no administrator exists" would rebuild the account
        // here, using the operator's original and long-since-leaked password.
        assertThat(runner.runBootstrap()).isFalse();

        assertThat(userRepository.findByUsername(USERNAME)).isEmpty();
    }

    // 8. Bootstrap credential is never stored as plaintext.
    @Test
    void theCredentialIsStoredOnlyAsAHash() {
        runner.runBootstrap();

        String storedHash =
                jdbcTemplate.queryForObject(
                        "SELECT password_hash FROM users WHERE username = ?",
                        String.class,
                        USERNAME);

        assertThat(storedHash).isNotEqualTo(PASSWORD).doesNotContain(PASSWORD).startsWith("$2");
        assertThat(passwordEncoder.matches(PASSWORD, storedHash)).isTrue();
    }

    @Test
    void thePlaintextCredentialAppearsNowhereInTheDatabase() {
        runner.runBootstrap();

        // Sweeps every text-bearing column the story touches, not just the one expected to hold it.
        assertThat(rowsContaining("users", List.of("username", "password_hash", "first_name", "last_name", "email")))
                .isZero();
        assertThat(rowsContaining("bootstrap_completions", List.of("administrator_username")))
                .isZero();
        // Includes the JSON payload columns, the most plausible place a credential would land.
        assertThat(
                        rowsContaining(
                                "audit_logs",
                                List.of(
                                        "action",
                                        "entity_type",
                                        "user_agent",
                                        "old_values::text",
                                        "new_values::text")))
                .isZero();
    }

    // 9. Created administrator requires password change.
    @Test
    void theAdministratorIsCreatedRequiringAPasswordChangeAndHoldingTheApprovedRole() {
        runner.runBootstrap();

        Boolean flag =
                jdbcTemplate.queryForObject(
                        "SELECT is_password_change_required FROM users WHERE username = ?",
                        Boolean.class,
                        USERNAME);
        assertThat(flag).isTrue();

        assertThat(roleNamesOf(USERNAME)).containsExactly(RoleName.SUPER_ADMINISTRATOR);
    }

    // 10. SYSTEM audit event exists for successful bootstrap.
    @Test
    void successRecordsASystemAuditEventThroughTheExistingRecorder() {
        runner.runBootstrap();

        // Scoped to this administrator: audit rows are immutable, so rows from earlier tests in
        // this class cannot be cleaned up and a global count would drift.
        java.util.UUID adminId = userRepository.findByUsername(USERNAME).orElseThrow().getId();
        List<java.util.Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT actor_user_id, entity_type, entity_id FROM audit_logs"
                                + " WHERE action = ? AND entity_id = ?",
                        FirstAdministratorBootstrap.ADMINISTRATOR_CREATED,
                        adminId);

        assertThat(rows).hasSize(1);
        // ADR-016: no human principal existed, and the account being created was the first row in
        // the users table.
        assertThat(rows.get(0).get("actor_user_id")).isNull();
        assertThat(rows.get(0).get("entity_type")).isEqualTo("User");
        assertThat(rows.get(0).get("entity_id")).isEqualTo(adminId);
    }

    // 11. Failed bootstrap does not incorrectly mark completion.
    @Test
    void aFailureAfterTheMarkerLeavesNoCompletionBehind() {
        // A duplicate username makes the administrator insert fail after the transaction has begun.
        jdbcTemplate.update(
                "INSERT INTO users (id, username, password_hash, first_name, last_name)"
                        + " VALUES (?, ?, ?, ?, ?)",
                java.util.UUID.randomUUID(),
                USERNAME,
                "pre-existing",
                "Pre",
                "Existing");

        assertThatThrownBy(() -> runner.runBootstrap()).isInstanceOf(RuntimeException.class);

        // Had the marker committed independently, bootstrap would be permanently disabled with no
        // administrator ever created -- an unrecoverable state.
        assertThat(completionRepository.count()).isZero();
    }

    private long administratorCount() {
        Long count =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM users WHERE username = ?", Long.class, USERNAME);
        return count == null ? 0L : count;
    }

    private List<String> roleNamesOf(String username) {
        return jdbcTemplate.queryForList(
                "SELECT r.name FROM roles r"
                        + " JOIN user_roles ur ON ur.role_id = r.id"
                        + " JOIN users u ON u.id = ur.user_id"
                        + " WHERE u.username = ?",
                String.class,
                username);
    }

    private long rowsContaining(String table, List<String> columns) {
        // position(), not LIKE: a password containing % or _ would otherwise be treated as a
        // wildcard pattern and the sweep would pass or fail for the wrong reason.
        String predicate =
                columns.stream()
                        .map(column -> "position(? in coalesce(" + column + ", '')) > 0")
                        .collect(java.util.stream.Collectors.joining(" OR "));
        Object[] args = columns.stream().map(column -> PASSWORD).toArray();
        Long count =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM " + table + " WHERE " + predicate, Long.class, args);
        return count == null ? 0L : count;
    }
}
