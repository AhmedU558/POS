package com.pos.auth;

import com.pos.AbstractIntegrationTest;
import com.pos.audit.domain.AuditEvent;
import com.pos.audit.domain.AuditRequestContext;
import com.pos.audit.service.AuditRecorder;
import com.pos.auth.service.PasswordChangeService;
import com.pos.users.domain.User;
import com.pos.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.pos.audit.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the atomicity the service claims, by failing a step that happens <em>after</em> the write.
 *
 * <p>The other transaction tests all reject before the first write, so they would pass with no
 * transaction at all. That left {@code @Transactional} pinned only by accident: {@code
 * AuditRecorder} is {@code Propagation.MANDATORY} and would throw without one. Relax the recorder
 * to {@code REQUIRES_NEW}, or swallow the audit exception, and a password could rotate leaving no
 * audit row — with every test still green.
 *
 * <p>Here the audit write is made to fail once the new hash has already been flushed, which is the
 * only ordering that can distinguish a real transaction from none.
 */
@Import(PasswordChangeAtomicityTests.FailingAuditConfiguration.class)
class PasswordChangeAtomicityTests extends AbstractIntegrationTest {

    private static final String CURRENT = "current-password-value";

    private String username;

    /**
     * A recorder that always fails, substituted for the real one.
     *
     * <p>A real subclass rather than a mock: Mockito's inline mock maker cannot instrument classes
     * on this JDK, and this is clearer anyway — the failure is ordinary Java, and the bean still
     * carries the genuine {@code Propagation.MANDATORY} semantics of the class it replaces.
     */
    @TestConfiguration
    static class FailingAuditConfiguration {

        @Bean
        @Primary
        AuditRecorder failingAuditRecorder(AuditLogRepository auditLogRepository) {
            return new AuditRecorder(auditLogRepository) {
                @Override
                public com.pos.audit.domain.AuditLog record(AuditEvent event) {
                    throw new IllegalStateException("audit sink unavailable");
                }
            };
        }
    }

    @Autowired private PasswordChangeService passwordChangeService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void createFlaggedUser() {
        username = "atomic." + java.util.UUID.randomUUID();
        User user = new User(username, passwordEncoder.encode(CURRENT), "Atomic", "User");
        user.requirePasswordChange();
        userRepository.saveAndFlush(user);
    }

    @Test
    void anAuditFailureAfterTheHashIsWrittenRollsTheWholeChangeBack() {
        assertThatThrownBy(
                        () ->
                                passwordChangeService.changePassword(
                                        username,
                                        CURRENT,
                                        "a-brand-new-password",
                                        AuditRequestContext.none()))
                .isInstanceOf(IllegalStateException.class);

        // Read straight from the database: the hash was flushed inside the transaction, so a
        // repository read could show it even after a rollback.
        String storedHash =
                jdbcTemplate.queryForObject(
                        "SELECT password_hash FROM users WHERE username = ?", String.class, username);
        Boolean storedFlag =
                jdbcTemplate.queryForObject(
                        "SELECT is_password_change_required FROM users WHERE username = ?",
                        Boolean.class,
                        username);

        assertThat(passwordEncoder.matches(CURRENT, storedHash))
                .as("the retired credential must still be the stored one")
                .isTrue();
        assertThat(passwordEncoder.matches("a-brand-new-password", storedHash)).isFalse();
        assertThat(storedFlag)
                .as("the rotation requirement must not have been cleared")
                .isTrue();
    }

    @Test
    void aChangeCannotSucceedWithoutItsAuditRowBeingWritten() {
        assertThatThrownBy(
                        () ->
                                passwordChangeService.changePassword(
                                        username,
                                        CURRENT,
                                        "a-brand-new-password",
                                        AuditRequestContext.none()))
                .isInstanceOf(IllegalStateException.class);

        java.util.UUID userId = userRepository.findByUsername(username).orElseThrow().getId();
        Long rows =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM audit_logs WHERE entity_id = ?", Long.class, userId);

        assertThat(rows).isZero();
    }
}
