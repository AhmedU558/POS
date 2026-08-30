package com.pos.users;

import com.pos.AbstractIntegrationTest;
import com.pos.users.domain.PermissionCode;
import com.pos.users.domain.Role;
import com.pos.users.domain.RoleName;
import com.pos.users.domain.User;
import com.pos.users.repository.PermissionRepository;
import com.pos.users.repository.RoleRepository;
import com.pos.users.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Verifies the entity mapping against a real database.
 *
 * <p>Transactional so the role graph can be traversed and so each test rolls back, leaving the
 * shared container clean for the rest of the suite.
 */
@Transactional
class IdentityPersistenceTests extends AbstractIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PermissionRepository permissionRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private org.springframework.core.env.Environment environment;

    @Test
    void schemaValidationIsActiveSoEntityDriftCannotPassSilently() {
        // Every context start in this suite doubles as an entity/migration conformance check --
        // but only while ddl-auto is `validate`. Relaxing it to `none` would remove that guard
        // without a single test turning red, so the guard itself is asserted here.
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }

    @Test
    void seededRolesAndPermissionsLoadThroughTheirRepositories() {
        assertThat(roleRepository.findByName(RoleName.SUPER_ADMINISTRATOR)).isPresent();
        assertThat(permissionRepository.findByCode(PermissionCode.USER_ADMIN)).isPresent();
        assertThat(roleRepository.count()).isEqualTo(RoleName.ALL.size());
    }

    @Test
    void unknownRoleLookupReturnsEmptyRatherThanThrowing() {
        Optional<Role> found = roleRepository.findByName("Chief Vibes Officer");

        assertThat(found).isEmpty();
    }

    @Test
    void permissionsResolveThroughTheRoleGraph() {
        Role superAdmin = roleRepository.findByName(RoleName.SUPER_ADMINISTRATOR).orElseThrow();

        assertThat(superAdmin.permissionCodes())
                .containsExactlyInAnyOrder("USER_READ", "USER_WRITE", "USER_ADMIN", "ROLE_READ", "ROLE_WRITE", "STORE_READ", "STORE_WRITE", "TERMINAL_READ", "TERMINAL_WRITE", "REGISTER_READ", "REGISTER_WRITE", "PRODUCT_READ", "PRODUCT_WRITE", "PRODUCT_PRICE_WRITE", "INVENTORY_READ", "INVENTORY_ADJUST", "INVENTORY_RECEIVE", "REPORT_INVENTORY", "CUSTOMER_READ", "CUSTOMER_WRITE", "CREDIT_READ", "CREDIT_WRITE", "SUPPLIER_READ", "SUPPLIER_WRITE", "PURCHASE_READ", "PURCHASE_WRITE", "PURCHASE_APPROVE", "AP_READ", "AP_WRITE");
    }

    @Test
    void permissionsFlattenAcrossAllOfAUsersRoles() {
        User user = newUser("graph.probe");
        user.assignRole(roleRepository.findByName(RoleName.SUPER_ADMINISTRATOR).orElseThrow());
        user.assignRole(roleRepository.findByName(RoleName.CASHIER).orElseThrow());
        userRepository.saveAndFlush(user);

        User reloaded = userRepository.findByUsername("graph.probe").orElseThrow();

        assertThat(reloaded.getRoles()).hasSize(2);
        // Cashier contributes PRODUCT_READ now, which is already in Super Administrator set.
        assertThat(reloaded.permissionCodes())
                .containsExactlyInAnyOrder("USER_READ", "USER_WRITE", "USER_ADMIN", "ROLE_READ", "ROLE_WRITE", "STORE_READ", "STORE_WRITE", "TERMINAL_READ", "TERMINAL_WRITE", "REGISTER_READ", "REGISTER_WRITE", "PRODUCT_READ", "PRODUCT_WRITE", "PRODUCT_PRICE_WRITE", "INVENTORY_READ", "INVENTORY_ADJUST", "INVENTORY_RECEIVE", "REPORT_INVENTORY", "CUSTOMER_READ", "CUSTOMER_WRITE", "CREDIT_READ", "CREDIT_WRITE", "SUPPLIER_READ", "SUPPLIER_WRITE", "PURCHASE_READ", "PURCHASE_WRITE", "PURCHASE_APPROVE", "AP_READ", "AP_WRITE");
    }

    @Test
    void fetchJoinLoadsTheWholeAuthorizationGraphInOneGo() {
        User user = newUser("fetch.probe");
        user.assignRole(roleRepository.findByName(RoleName.SUPER_ADMINISTRATOR).orElseThrow());
        userRepository.saveAndFlush(user);
        entityManager.clear();

        User loaded =
                userRepository.findByUsernameWithRolesAndPermissions("fetch.probe").orElseThrow();

        assertThat(loaded.permissionCodes()).contains(PermissionCode.ROLE_WRITE);
    }

    @Test
    void timestampsArePopulatedWithoutADatabaseTrigger() {
        User saved = userRepository.saveAndFlush(newUser("timestamp.probe"));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void updatingAUserAdvancesTheUpdatedTimestamp() throws InterruptedException {
        User saved = userRepository.saveAndFlush(newUser("touch.probe"));
        Instant createdAt = saved.getCreatedAt();
        Instant updatedBefore = saved.getUpdatedAt();

        // The database stores microseconds; without a gap the two writes can land on the same
        // tick and a strict comparison would flake.
        Thread.sleep(5);

        saved.setActive(false);
        userRepository.saveAndFlush(saved);
        entityManager.refresh(saved);

        // Strictly after the previous updatedAt, not merely after createdAt. Comparing against
        // createdAt would still pass with @UpdateTimestamp removed, since the schema also
        // defaults updated_at on insert.
        assertThat(saved.getUpdatedAt()).isAfter(updatedBefore);
        assertThat(saved.getCreatedAt()).isCloseTo(createdAt, within(1, ChronoUnit.MILLIS));
    }

    @Test
    void usersDefaultToActive() {
        User saved = userRepository.saveAndFlush(newUser("active.probe"));

        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void removingAUserClearsItsRoleLinksButNotTheRolesThemselves() {
        User user = newUser("cascade.probe");
        user.assignRole(roleRepository.findByName(RoleName.ACCOUNTANT).orElseThrow());
        User saved = userRepository.saveAndFlush(user);
        UUID userId = saved.getId();

        userRepository.delete(saved);
        userRepository.flush();

        assertThat(linkCountFor(userId)).isZero();
        assertThat(roleRepository.findByName(RoleName.ACCOUNTANT)).isPresent();
        assertThat(roleRepository.count()).isEqualTo(RoleName.ALL.size());
        // Asserts the identity codes survived, not a global total that any future module would
        // break by seeding its own codes.
        assertThat(permissionRepository.findByCode(PermissionCode.USER_READ)).isPresent();
    }

    @Test
    void toStringNeverRevealsThePasswordHash() {
        User user = newUser("secret.probe");

        assertThat(user.toString())
                .contains("secret.probe")
                .doesNotContain("hashed-value-must-not-leak");
    }

    private User newUser(String username) {
        return new User(username, "hashed-value-must-not-leak", "Test", "Probe");
    }

    private long linkCountFor(UUID userId) {
        Long count =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM user_roles WHERE user_id = ?", Long.class, userId);
        return count == null ? 0L : count;
    }
}
