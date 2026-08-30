package com.pos.common;

import com.pos.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Database integration tests.
 *
 * <p>Confirms that Flyway actually applied the baseline migration inside the container, and that
 * the resulting schema matches the Database Design and ERD Specification rather than merely
 * existing. Guards against the {@code baseline-on-migrate} failure mode where Flyway reports
 * success while silently skipping migrations.
 */
class FlywayMigrationTests extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void baselineMigrationIsRecordedAsApplied() {
        List<Map<String, Object>> history =
                jdbcTemplate.queryForList(
                        "SELECT version, description, success FROM flyway_schema_history"
                                + " ORDER BY installed_rank");

        assertThat(history).isNotEmpty();
        assertThat(history.get(0))
                .containsEntry("version", "1")
                .containsEntry("success", true);
    }

    @Test
    void identityTablesFromTheSpecificationExist() {
        List<String> tables =
                jdbcTemplate.queryForList(
                        "SELECT table_name FROM information_schema.tables"
                                + " WHERE table_schema = 'public'",
                        String.class);

        // Database Design & ERD Specification section 6, "Identity & Access Tables".
        assertThat(tables)
                .contains("users", "roles", "permissions", "user_roles", "role_permissions");
    }

    @Test
    void usersTableMatchesTheSpecifiedColumnContract() {
        Map<String, String> columns = columnTypesOf("users");

        // Database Design & ERD Specification section 6.1.
        assertThat(columns)
                .containsEntry("id", "uuid")
                .containsEntry("username", "character varying")
                .containsEntry("email", "character varying")
                .containsEntry("password_hash", "character varying")
                .containsEntry("first_name", "character varying")
                .containsEntry("last_name", "character varying")
                .containsEntry("is_active", "boolean")
                .containsEntry("last_login_at", "timestamp with time zone")
                .containsEntry("created_at", "timestamp with time zone")
                .containsEntry("updated_at", "timestamp with time zone");
    }

    @Test
    void applicationTablesAvoidTimezoneNaiveTimestampColumns() {
        // Section 3 requires UTC timestamps at persistence level, which means timestamptz.
        // flyway_schema_history is excluded: it is owned by the migration tool, not the schema.
        List<String> naiveTimestamps =
                jdbcTemplate.queryForList(
                        "SELECT table_name || '.' || column_name FROM information_schema.columns"
                                + " WHERE table_schema = 'public'"
                                + " AND table_name <> 'flyway_schema_history'"
                                + " AND data_type = 'timestamp without time zone'",
                        String.class);

        assertThat(naiveTimestamps).isEmpty();
    }

    @Test
    void usernameUniquenessIsEnforcedByTheDatabase() {
        insertUser("audit.probe");

        assertThatThrownBy(() -> insertUser("audit.probe"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void stockAlertsTableMatchesTheDerivedColumnContract() {
        Map<String, String> columns = columnTypesOf("stock_alerts");

        assertThat(columns)
                .containsEntry("id", "uuid")
                .containsEntry("store_id", "uuid")
                .containsEntry("product_id", "uuid")
                .containsEntry("batch_id", "uuid")
                .containsEntry("alert_type", "character varying")
                .containsEntry("quantity", "numeric")
                .containsEntry("minimum_level", "numeric")
                .containsEntry("expiration_date", "date")
                .containsEntry("status", "character varying")
                .containsEntry("acknowledged_at", "timestamp with time zone")
                .containsEntry("acknowledged_by", "uuid")
                .containsEntry("created_at", "timestamp with time zone")
                .containsEntry("updated_at", "timestamp with time zone");
    }

    @Test
    void inventoryBatchesTableMatchesTheSpecifiedColumnContract() {
        Map<String, String> columns = columnTypesOf("inventory_batches");

        assertThat(columns)
                .containsEntry("id", "uuid")
                .containsEntry("product_id", "uuid")
                .containsEntry("store_id", "uuid")
                .containsEntry("batch_number", "character varying")
                .containsEntry("quantity", "numeric")
                .containsEntry("expiration_date", "date")
                .containsEntry("manufacturing_date", "date")
                .containsEntry("created_at", "timestamp with time zone");
    }

    @Test
    void inventoryBatchesHasTheSpecifiedProductExpirationIndex() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_indexes"
                        + " WHERE schemaname = 'public'"
                        + " AND indexname = 'idx_inventory_batches_product_id_expiration_date'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void inventoryTransactionBatchIdReferencesInventoryBatches() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.table_constraints"
                        + " WHERE table_schema = 'public'"
                        + " AND table_name = 'inventory_transactions'"
                        + " AND constraint_name = 'fk_inventory_transactions_batch_id'"
                        + " AND constraint_type = 'FOREIGN KEY'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void referentialIntegrityIsEnforcedOnUserRoles() {
        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)",
                                        UUID.randomUUID(),
                                        UUID.randomUUID()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private void insertUser(String username) {
        jdbcTemplate.update(
                "INSERT INTO users (id, username, password_hash, first_name, last_name)"
                        + " VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                username,
                "not-a-real-hash",
                "Audit",
                "Probe");
    }

    private Map<String, String> columnTypesOf(String table) {
        return jdbcTemplate
                .queryForList(
                        "SELECT column_name, data_type FROM information_schema.columns"
                                + " WHERE table_schema = 'public' AND table_name = ?",
                        table)
                .stream()
                .collect(
                        java.util.stream.Collectors.toMap(
                                row -> (String) row.get("column_name"),
                                row -> (String) row.get("data_type")));
    }
}
