package com.pos.auth;

import com.pos.AbstractIntegrationTest;
import com.pos.audit.domain.AuditRequestContext;
import com.pos.auth.service.PasswordChangeService;
import com.pos.common.exception.ApiException;
import com.pos.users.domain.User;
import com.pos.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Transaction and concurrency behaviour of a password change.
 *
 * <p>Not transactional at class level: the point is what survives a commit boundary, which a
 * test-managed rollback would hide.
 */
class PasswordChangeTransactionTests extends AbstractIntegrationTest {

    /*
     * Usernames are unique per test and accounts are never deleted.
     *
     * A successful change writes an audit row naming the acting user, and audit_logs has no
     * ON DELETE clause, so that account can never be removed (ADR-016). Deleting fixtures would
     * fail for the very reason the audit trail exists.
     */
    private String username;
    private static final String CURRENT = "current-password-value";

    @Autowired private PasswordChangeService passwordChangeService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void createFlaggedUser() {
        username = "tx.rotating." + java.util.UUID.randomUUID();
        User user = new User(username, passwordEncoder.encode(CURRENT), "Tx", "User");
        user.requirePasswordChange();
        userRepository.saveAndFlush(user);
    }

    @Test
    void aRejectedChangeCommitsNothing() {
        assertThatThrownBy(
                        () ->
                                passwordChangeService.changePassword(
                                        username,
                                        "wrong-current",
                                        "a-brand-new-password",
                                        AuditRequestContext.none()))
                .isInstanceOf(ApiException.class);

        assertStoredState(CURRENT, true);
    }

    @Test
    void aPolicyRejectionCommitsNothing() {
        assertThatThrownBy(
                        () ->
                                passwordChangeService.changePassword(
                                        username, CURRENT, "short", AuditRequestContext.none()))
                .isInstanceOf(ApiException.class);

        assertStoredState(CURRENT, true);
    }

    @Test
    void anUnknownPrincipalIsRejectedTheSameWayAsABadPassword() {
        // An authenticated principal naming an account that no longer exists must not be able to
        // tell that apart from a wrong password.
        assertThatThrownBy(
                        () ->
                                passwordChangeService.changePassword(
                                        "no.such.user",
                                        CURRENT,
                                        "a-brand-new-password",
                                        AuditRequestContext.none()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("current password is not correct");
    }

    @Test
    void aSuccessfulChangeCommitsTheHashAndTheClearedFlagTogether() {
        passwordChangeService.changePassword(username, CURRENT, "a-brand-new-password", AuditRequestContext.none());

        assertStoredState("a-brand-new-password", false);
    }

    @Test
    void theOldPasswordStopsWorkingImmediately() {
        passwordChangeService.changePassword(username, CURRENT, "a-brand-new-password", AuditRequestContext.none());

        assertThatThrownBy(
                        () ->
                                passwordChangeService.changePassword(
                                        username,
                                        CURRENT,
                                        "another-new-password",
                                        AuditRequestContext.none()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void concurrentChangesLeaveTheAccountInAConsistentState() throws Exception {
        int attempts = 4;
        CyclicBarrier startLine = new CyclicBarrier(attempts);
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        try {
            List<Callable<Boolean>> requests =
                    IntStream.range(0, attempts)
                            .<Callable<Boolean>>mapToObj(
                                    index ->
                                            () -> {
                                                startLine.await(10, TimeUnit.SECONDS);
                                                try {
                                                    passwordChangeService.changePassword(
                                                            username,
                                                            CURRENT,
                                                            "replacement-password-" + index, AuditRequestContext.none());
                                                    return true;
                                                } catch (RuntimeException ex) {
                                                    return false;
                                                }
                                            })
                            .toList();

            List<Future<Boolean>> futures = pool.invokeAll(requests, 60, TimeUnit.SECONDS);
            long succeeded = futures.stream().filter(PasswordChangeTransactionTests::resolve).count();

            // Optimistic locking is deferred, so this is last-write-wins and more than one may
            // succeed. The invariant that matters is the one asserted below.
            assertThat(succeeded).isPositive();
        } finally {
            pool.shutdownNow();
        }

        User reloaded = userRepository.findByUsername(username).orElseThrow();
        // Whatever the interleaving, the flag is never cleared without a password behind it, and
        // the surviving hash is one of the replacements -- never the retired credential.
        assertThat(reloaded.isPasswordChangeRequired()).isFalse();
        assertThat(passwordEncoder.matches(CURRENT, reloaded.getPasswordHash())).isFalse();
        assertThat(
                        IntStream.range(0, 4)
                                .anyMatch(
                                        index ->
                                                passwordEncoder.matches(
                                                        "replacement-password-" + index,
                                                        reloaded.getPasswordHash())))
                .isTrue();
    }

    private static boolean resolve(Future<Boolean> future) {
        try {
            return future.get();
        } catch (Exception ex) {
            return false;
        }
    }

    private void assertStoredState(String expectedPassword, boolean expectedFlag) {
        String storedHash =
                jdbcTemplate.queryForObject(
                        "SELECT password_hash FROM users WHERE username = ?", String.class, username);
        Boolean storedFlag =
                jdbcTemplate.queryForObject(
                        "SELECT is_password_change_required FROM users WHERE username = ?",
                        Boolean.class,
                        username);

        assertThat(passwordEncoder.matches(expectedPassword, storedHash)).isTrue();
        assertThat(storedFlag).isEqualTo(expectedFlag);
    }
}
