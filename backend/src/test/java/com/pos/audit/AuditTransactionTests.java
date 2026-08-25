package com.pos.audit;

import com.pos.AbstractIntegrationTest;
import com.pos.audit.domain.AuditActor;
import com.pos.audit.domain.AuditEvent;
import com.pos.audit.service.AuditRecorder;
import com.pos.users.domain.User;
import com.pos.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that an audit record shares the fate of the action it describes.
 *
 * <p>Deliberately <strong>not</strong> {@code @Transactional}: the point is to observe what
 * survives a commit boundary. A test-managed transaction would roll everything back regardless and
 * could not tell a joined transaction from an independent one.
 */
@Import(AuditTransactionTests.TestBeans.class)
class AuditTransactionTests extends AbstractIntegrationTest {

    private static final String ENTITY_TYPE = "TransactionProbe";

    @Autowired private AuditRecorder auditRecorder;
    @Autowired private AuditedOperations auditedOperations;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void auditRecordSurvivesWhenTheOperationCommits() {
        UUID entityId = UUID.randomUUID();

        auditedOperations.succeed(entityId);

        assertThat(auditRowsFor(entityId)).isEqualTo(1);
    }

    @Test
    void auditRecordIsRolledBackWhenTheOperationFails() {
        UUID entityId = UUID.randomUUID();

        assertThatThrownBy(() -> auditedOperations.failAfterRecording(entityId))
                .isInstanceOf(IllegalStateException.class);

        // If the recorder ran in its own transaction, this row would have committed and would now
        // claim an action that never happened.
        assertThat(auditRowsFor(entityId)).isZero();
    }

    /**
     * Transactional, unlike the rest of this class, and deliberately so: an audit row naming a user
     * pins that user permanently, because audit rows cannot be deleted. Committing one here would
     * leave a user no later test could ever remove.
     */
    @Test
    @Transactional
    void aUserWithAuditHistoryCannotBeDeleted() {
        User probe =
                userRepository.saveAndFlush(
                        new User("tx.probe", "hashed-value-must-not-leak", "Tx", "Probe"));
        auditedOperations.recordAgainstUser(probe.getId());
        entityManager.flush();

        // The trail pins the actor it names: audit_logs has no ON DELETE clause, so history
        // outranks account cleanup. Deactivation is the supported path (Database Design §3).
        assertThatThrownBy(
                        () -> {
                            userRepository.delete(probe);
                            userRepository.flush();
                        })
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void recordingOutsideATransactionIsRefused() {
        UUID entityId = UUID.randomUUID();

        // The guarantee this class exists to protect is "the audit row shares the caller's fate".
        // Propagation.REQUIRED would quietly start a transaction here and commit the row on its
        // own -- the exact behaviour the recorder's javadoc rejects, and invisible at the call
        // site. MANDATORY turns it into an immediate failure.
        assertThatThrownBy(
                        () ->
                                auditRecorder.record(
                                        AuditEvent.of(
                                                AuditActor.system(),
                                                "PROBE_UNBOUND",
                                                ENTITY_TYPE,
                                                entityId)))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(auditRowsFor(entityId)).isZero();
    }

    private long auditRowsFor(UUID entityId) {
        Long count =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM audit_logs WHERE entity_type = ? AND entity_id = ?",
                        Long.class,
                        ENTITY_TYPE,
                        entityId);
        return count == null ? 0L : count;
    }

    /** Stands in for a business service that audits part of its work. */
    static class AuditedOperations {

        private final AuditRecorder auditRecorder;

        AuditedOperations(AuditRecorder auditRecorder) {
            this.auditRecorder = auditRecorder;
        }

        @Transactional
        public void succeed(UUID entityId) {
            auditRecorder.record(
                    AuditEvent.of(AuditActor.system(), "PROBE_OK", ENTITY_TYPE, entityId));
        }

        @Transactional
        public void failAfterRecording(UUID entityId) {
            auditRecorder.record(
                    AuditEvent.of(AuditActor.system(), "PROBE_DOOMED", ENTITY_TYPE, entityId));
            throw new IllegalStateException("business rule failed after the audit was written");
        }

        @Transactional
        public void recordAgainstUser(UUID userId) {
            auditRecorder.record(
                    AuditEvent.of(AuditActor.user(userId), "PROBE_ACTOR", ENTITY_TYPE, userId));
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        AuditedOperations auditedOperations(AuditRecorder auditRecorder) {
            return new AuditedOperations(auditRecorder);
        }
    }
}
