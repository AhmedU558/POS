package com.pos.users;

import com.pos.users.domain.Permission;
import com.pos.users.domain.PermissionCode;
import com.pos.users.domain.Role;
import com.pos.users.domain.RoleName;
import com.pos.users.domain.User;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plain unit tests for the identity domain objects.
 *
 * <p>No Spring context and no Docker: these are in-memory behaviours, and pinning them to the
 * container suite would make the fast feedback loop cost twenty seconds and a running daemon.
 */
class IdentityDomainTests {

    @Test
    void permissionsWithTheSameCodeAreTheSamePermission() {
        Permission first = new Permission(PermissionCode.USER_READ, "one description");
        Permission second = new Permission(PermissionCode.USER_READ, "another description");

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        // A mutable set, because Set.of rejects duplicates outright instead of collapsing them.
        assertThat(new HashSet<>(List.of(first, second))).hasSize(1);
    }

    @Test
    void permissionsWithDifferentCodesAreDistinct() {
        assertThat(new Permission(PermissionCode.USER_READ, null))
                .isNotEqualTo(new Permission(PermissionCode.USER_WRITE, null));
    }

    @Test
    void aPermissionCannotBeCreatedWithoutACode() {
        // A null business key would make every such instance equal to every other, silently
        // collapsing them into one set element.
        assertThatThrownBy(() -> new Permission(null, "no code"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void aRoleCannotBeCreatedWithoutAName() {
        assertThatThrownBy(() -> new Role(null, "no name")).isInstanceOf(NullPointerException.class);
    }

    @Test
    void aUserCannotBeCreatedWithoutAUsernameOrHash() {
        assertThatThrownBy(() -> new User(null, "hash", "A", "B"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new User("name", null, "A", "B"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void grantingAndRevokingChangesTheRolesCodes() {
        Role role = new Role(RoleName.CASHIER, "till operator");
        Permission read = new Permission(PermissionCode.USER_READ, null);

        role.grant(read);
        assertThat(role.permissionCodes()).containsExactly(PermissionCode.USER_READ);

        role.revoke(read);
        assertThat(role.permissionCodes()).isEmpty();
    }

    @Test
    void grantingNullIsRejectedAtTheCallSite() {
        // Otherwise the NullPointerException surfaces later, inside permissionCodes(), far from
        // whoever actually passed the null.
        assertThatThrownBy(() -> new Role(RoleName.CASHIER, null).grant(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void assigningNullRoleIsRejectedAtTheCallSite() {
        assertThatThrownBy(() -> newUser().assignRole(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void permissionCodesFlattenAcrossRolesAndDeduplicate() {
        Permission shared = new Permission(PermissionCode.USER_READ, null);
        Role first = new Role(RoleName.CASHIER, null);
        Role second = new Role(RoleName.ACCOUNTANT, null);
        first.grant(shared);
        second.grant(shared);
        second.grant(new Permission(PermissionCode.ROLE_READ, null));

        User user = newUser();
        user.assignRole(first);
        user.assignRole(second);

        assertThat(user.permissionCodes())
                .containsExactlyInAnyOrder(PermissionCode.USER_READ, PermissionCode.ROLE_READ);
    }

    @Test
    void roleAndPermissionCollectionsCannotBeMutatedThroughTheirGetters() {
        Role role = new Role(RoleName.CASHIER, null);
        User user = newUser();
        user.assignRole(role);

        assertThatThrownBy(() -> user.getRoles().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> role.getPermissions().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void removingARoleDetachesItFromTheUserOnly() {
        Role role = new Role(RoleName.ACCOUNTANT, null);
        User user = newUser();
        user.assignRole(role);

        user.removeRole(role);

        assertThat(user.getRoles()).isEmpty();
        assertThat(role.getName()).isEqualTo(RoleName.ACCOUNTANT);
    }

    @Test
    void toStringNeverCarriesThePasswordHash() {
        assertThat(newUser().toString()).contains("unit.probe").doesNotContain("super-secret-hash");
    }

    @Test
    void theSeededCodeAndRoleCataloguesMatchTheApprovedDocuments() {
        assertThat(PermissionCode.IDENTITY).hasSize(5);
        assertThat(RoleName.ALL).hasSize(6).contains(RoleName.SUPER_ADMINISTRATOR);
    }

    private User newUser() {
        return new User("unit.probe", "super-secret-hash", "Unit", "Probe");
    }
}
