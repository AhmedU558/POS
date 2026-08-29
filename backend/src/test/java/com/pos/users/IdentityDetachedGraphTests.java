package com.pos.users;

import com.pos.AbstractIntegrationTest;
import com.pos.users.domain.PermissionCode;
import com.pos.users.domain.RoleName;
import com.pos.users.domain.User;
import com.pos.users.repository.RoleRepository;
import com.pos.users.repository.UserRepository;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the fetch-join query actually loads the authorization graph eagerly.
 *
 * <p>Deliberately <strong>not</strong> {@code @Transactional}. Inside an open transaction a lazy
 * query and a fetch-join query behave identically, because the session is still there to
 * initialize the collections on demand — so a transactional test cannot tell them apart and would
 * keep passing after someone deleted the {@code JOIN FETCH} clauses.
 *
 * <p>Reading after the transaction has ended is the only way to observe the difference, which is
 * also the condition a real authenticated request runs under.
 */
class IdentityDetachedGraphTests extends AbstractIntegrationTest {

    private static final String USERNAME = "detached.probe";

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;

    @BeforeEach
    void createUserWithRoles() {
        User user = new User(USERNAME, "hashed-value-must-not-leak", "Detached", "Probe");
        user.assignRole(roleRepository.findByName(RoleName.SUPER_ADMINISTRATOR).orElseThrow());
        userRepository.saveAndFlush(user);
    }

    /** No surrounding transaction rolls this back, so it is removed explicitly. */
    @AfterEach
    void removeUser() {
        userRepository.findByUsername(USERNAME).ifPresent(userRepository::delete);
    }

    @Test
    void fetchJoinQueryResolvesPermissionsAfterTheTransactionEnds() {
        User loaded = userRepository.findByUsernameWithRolesAndPermissions(USERNAME).orElseThrow();

        // Fails with LazyInitializationException if the JOIN FETCH clauses are removed.
        assertThat(loaded.permissionCodes())
                .containsExactlyInAnyOrder("USER_READ", "USER_WRITE", "USER_ADMIN", "ROLE_READ", "ROLE_WRITE", "STORE_READ", "STORE_WRITE", "TERMINAL_READ", "TERMINAL_WRITE", "REGISTER_READ", "REGISTER_WRITE", "PRODUCT_READ", "PRODUCT_WRITE", "PRODUCT_PRICE_WRITE");
    }

    @Test
    void plainLookupCannotResolvePermissionsOnceDetached() {
        // The counter-example that gives the test above its meaning: without the fetch joins,
        // this is what an authorization check outside a transaction would hit.
        User loaded = userRepository.findByUsername(USERNAME).orElseThrow();

        assertThatThrownBy(loaded::permissionCodes)
                .isInstanceOf(LazyInitializationException.class);
    }
}
