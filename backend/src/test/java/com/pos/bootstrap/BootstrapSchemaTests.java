package com.pos.bootstrap;

import com.pos.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the V4 and V5 schema against AMD-001, and the constraints that carry the security
 * properties rather than merely describing them.
 */
@Transactional
class BootstrapSchemaTests extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void usersCarriesTheApprovedRotationColumn() {
        Map<String, String> column = columnMetadata("users", "is_password_change_required");

        assertThat(column).containsEntry("data_type", "boolean").containsEntry("is_nullable", "NO");
        assertThat(column.get("column_default")).isEqualTo("false");
    }

    @Test
    void existingAccountsAreUnaffectedByTheNewColumn() {
        // AMD-001 promises the column is inert unless something deliberately sets it.
        jdbcTemplate.update(
                "INSERT INTO users (id, username, password_hash, first_name, last_name)"
                        + " VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                "default.probe",
                "hash",
                "Default",
                "Probe");

        Boolean flag =
                jdbcTemplate.queryForObject(
                        "SELECT is_password_change_required FROM users WHERE username = ?",
                        Boolean.class,
                        "default.probe");

        assertThat(flag).isFalse();
    }

    @Test
    void bootstrapCompletionsMatchesTheApprovedShape() {
        Map<String, String> columns =
                jdbcTemplate
                        .queryForList(
                                "SELECT column_name, data_type FROM information_schema.columns"
                                        + " WHERE table_schema = 'public'"
                                        + " AND table_name = 'bootstrap_completions'")
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        row -> (String) row.get("column_name"),
                                        row -> (String) row.get("data_type")));

        assertThat(columns)
                .containsOnlyKeys(
                        "id",
                        "is_singleton",
                        "completed_at",
                        "administrator_user_id",
                        "administrator_username")
                .containsEntry("is_singleton", "boolean")
                .containsEntry("completed_at", "timestamp with time zone")
                .containsEntry("administrator_user_id", "uuid")
                .containsEntry("administrator_username", "character varying");
    }

    @Test
    void atMostOneCompletionRowCanEverExist() {
        insertCompletion("first.admin");

        // The one-shot guarantee, carried by the database rather than by application logic.
        // Nothing is queried afterwards: a constraint violation aborts the transaction, so any
        // follow-up statement would fail for that reason instead of the one under test.
        assertThatThrownBy(() -> insertCompletion("second.admin"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aCompletionRowCannotClaimNotToBeTheSingleton() {
        // Without the CHECK, a row with is_singleton = false would slip past the unique index and
        // a second bootstrap could be recorded.
        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "INSERT INTO bootstrap_completions"
                                                + " (id, is_singleton, administrator_username)"
                                                + " VALUES (?, false, ?)",
                                        UUID.randomUUID(),
                                        "sneaky.admin"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingTheAdministratorLeavesTheMarkerBehind() {
        UUID adminId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, username, password_hash, first_name, last_name)"
                        + " VALUES (?, ?, ?, ?, ?)",
                adminId,
                "deletable.admin",
                "hash",
                "Deletable",
                "Admin");
        jdbcTemplate.update(
                "INSERT INTO bootstrap_completions (id, administrator_user_id, administrator_username)"
                        + " VALUES (?, ?, ?)",
                UUID.randomUUID(),
                adminId,
                "deletable.admin");

        jdbcTemplate.update("DELETE FROM users WHERE id = ?", adminId);

        // ON DELETE SET NULL rather than CASCADE: the marker is what stops a restart rebuilding
        // the account, so it must outlive the account.
        assertThat(completionCount()).isEqualTo(1);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT administrator_username FROM bootstrap_completions",
                                String.class))
                .isEqualTo("deletable.admin");
        assertThat(
                        jdbcTemplate.queryForList(
                                "SELECT administrator_user_id FROM bootstrap_completions",
                                UUID.class))
                .containsExactly((UUID) null);
    }

    private void insertCompletion(String username) {
        jdbcTemplate.update(
                "INSERT INTO bootstrap_completions (id, administrator_username) VALUES (?, ?)",
                UUID.randomUUID(),
                username);
    }

    private long completionCount() {
        Long count =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM bootstrap_completions", Long.class);
        return count == null ? 0L : count;
    }

    private Map<String, String> columnMetadata(String table, String column) {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT data_type, is_nullable, column_default"
                                + " FROM information_schema.columns"
                                + " WHERE table_schema = 'public' AND table_name = ?"
                                + " AND column_name = ?",
                        table,
                        column);
        assertThat(rows).hasSize(1);
        return rows.get(0).entrySet().stream()
                .collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> String.valueOf(entry.getValue())));
    }
}
