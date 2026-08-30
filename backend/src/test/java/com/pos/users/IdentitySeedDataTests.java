package com.pos.users;

import com.pos.AbstractIntegrationTest;
import com.pos.users.domain.PermissionCode;
import com.pos.users.domain.RoleName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the V2 reference data at the SQL level.
 *
 * <p>Deliberately not routed through the repositories: this asserts what the migration actually
 * wrote, independently of whether the entity mapping happens to agree with it.
 *
 * <p>Transactional so the idempotency test cannot leave rows behind for the rest of the suite if
 * a conflict clause ever regresses.
 */
@Transactional
class IdentitySeedDataTests extends AbstractIntegrationTest {

    private static final String SEED_MIGRATION = "db/migration/V2__seed_identity_reference_data.sql";

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void seedsExactlyTheApprovedRoles() {
        List<String> names = jdbcTemplate.queryForList("SELECT name FROM roles", String.class);

        assertThat(names).containsExactlyInAnyOrderElementsOf(RoleName.ALL);
    }

    @Test
    void everySeededRoleCarriesItsApprovedDescription() {
        String description =
                jdbcTemplate.queryForObject(
                        "SELECT description FROM roles WHERE name = ?",
                        String.class,
                        RoleName.CASHIER);

        assertThat(description)
                .isEqualTo(
                        "POS sales, customer registration, authorized discounts, returns,"
                                + " receipts, and register closing.");
    }

    @Test
    void seedsExactlyTheIdentityPermissionCodes() {
        List<String> codes = jdbcTemplate.queryForList("SELECT code FROM permissions", String.class);

        // Intentionally exhaustive. Codes for products, sales and inventory arrive with their own
        // modules, so this turning red means a module seeded its codes ahead of its endpoints.
        assertThat(codes).containsExactlyInAnyOrder("USER_READ", "USER_WRITE", "USER_ADMIN", "ROLE_READ", "ROLE_WRITE", "STORE_READ", "STORE_WRITE", "TERMINAL_READ", "TERMINAL_WRITE", "REGISTER_READ", "REGISTER_WRITE", "PRODUCT_READ", "PRODUCT_WRITE", "PRODUCT_PRICE_WRITE", "INVENTORY_READ", "INVENTORY_ADJUST", "INVENTORY_RECEIVE");
    }

    @Test
    void superAdministratorHoldsEveryIdentityPermission() {
        assertThat(permissionCodesOf(RoleName.SUPER_ADMINISTRATOR))
                .containsExactlyInAnyOrder("USER_READ", "USER_WRITE", "USER_ADMIN", "ROLE_READ", "ROLE_WRITE", "STORE_READ", "STORE_WRITE", "TERMINAL_READ", "TERMINAL_WRITE", "REGISTER_READ", "REGISTER_WRITE", "PRODUCT_READ", "PRODUCT_WRITE", "PRODUCT_PRICE_WRITE", "INVENTORY_READ", "INVENTORY_ADJUST", "INVENTORY_RECEIVE");
    }

    @Test
    void expectedRolesHoldTheirSeededPermissions() {
        // Assert other roles hold their granted permissions, and un-granted roles hold nothing.
        assertThat(permissionCodesOf(RoleName.STORE_MANAGER))
                .containsExactlyInAnyOrder("PRODUCT_READ", "PRODUCT_WRITE", "PRODUCT_PRICE_WRITE", "INVENTORY_READ", "INVENTORY_ADJUST", "INVENTORY_RECEIVE");
        assertThat(permissionCodesOf(RoleName.INVENTORY_MANAGER))
                .containsExactlyInAnyOrder("PRODUCT_READ", "PRODUCT_WRITE", "PRODUCT_PRICE_WRITE", "INVENTORY_READ", "INVENTORY_ADJUST", "INVENTORY_RECEIVE");
        assertThat(permissionCodesOf(RoleName.CASHIER))
                .containsExactlyInAnyOrder("PRODUCT_READ");

        assertThat(permissionCodesOf(RoleName.ACCOUNTANT)).isEmpty();
        assertThat(permissionCodesOf(RoleName.ONLINE_ORDER_STAFF)).isEmpty();
    }

    @Test
    void reapplyingTheActualMigrationChangesNothing() throws IOException {
        long rolesBefore = countOf("roles");
        long permissionsBefore = countOf("permissions");
        long grantsBefore = countOf("role_permissions");

        // Executes the real migration file, not a copy of it. A hand-written replica would keep
        // passing after someone deleted an ON CONFLICT clause from V2.
        jdbcTemplate.execute(readSeedMigration());

        assertThat(countOf("roles")).isEqualTo(rolesBefore);
        assertThat(countOf("permissions")).isEqualTo(permissionsBefore);
        assertThat(countOf("role_permissions")).isEqualTo(grantsBefore);
    }

    @Test
    void roleNamesAreUniqueSoTheSeedConflictClauseHasSomethingToCatch() {
        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "INSERT INTO roles (id, name) VALUES (?, ?)",
                                        UUID.randomUUID(),
                                        RoleName.CASHIER))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void permissionCodesAreUniqueSoTheSeedConflictClauseHasSomethingToCatch() {
        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "INSERT INTO permissions (id, code) VALUES (?, ?)",
                                        UUID.randomUUID(),
                                        PermissionCode.USER_READ))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void grantsAreUniquePerRoleAndPermission() {
        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "INSERT INTO role_permissions (role_id, permission_id)"
                                                + " SELECT r.id, p.id FROM roles r, permissions p"
                                                + " WHERE r.name = ? AND p.code = ?",
                                        RoleName.SUPER_ADMINISTRATOR,
                                        PermissionCode.USER_READ))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void migrationHistoryRecordsBothVersionsInOrder() {
        List<String> versions =
                jdbcTemplate.queryForList(
                        "SELECT version FROM flyway_schema_history"
                                + " WHERE success = true ORDER BY installed_rank",
                        String.class);

        assertThat(versions).containsSubsequence("1", "2");
    }

    private List<String> permissionCodesOf(String roleName) {
        return jdbcTemplate.queryForList(
                "SELECT p.code FROM permissions p"
                        + " JOIN role_permissions rp ON rp.permission_id = p.id"
                        + " JOIN roles r ON r.id = rp.role_id"
                        + " WHERE r.name = ?",
                String.class,
                roleName);
    }

    private long countOf(String table) {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class);
        return count == null ? 0L : count;
    }

    /** Flyway will not re-run an applied migration, so idempotency is exercised directly. */
    private String readSeedMigration() throws IOException {
        try (InputStreamReader reader =
                new InputStreamReader(
                        new ClassPathResource(SEED_MIGRATION).getInputStream(),
                        StandardCharsets.UTF_8)) {
            return FileCopyUtils.copyToString(reader);
        }
    }
}
