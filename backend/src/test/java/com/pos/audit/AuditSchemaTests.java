package com.pos.audit;

import com.pos.AbstractIntegrationTest;
import com.pos.audit.domain.AuditActor;
import com.pos.audit.domain.AuditEvent;
import com.pos.audit.service.AuditRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the audit schema against Database Design & ERD Specification §20.1 and §23, and the
 * immutability that SRS AUD-003 requires.
 */
class AuditSchemaTests extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AuditRecorder auditRecorder;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void auditLogsMatchesTheSpecifiedColumnContract() {
        Map<String, String> columns =
                jdbcTemplate
                        .queryForList(
                                "SELECT column_name, data_type FROM information_schema.columns"
                                        + " WHERE table_schema = 'public'"
                                        + " AND table_name = 'audit_logs'")
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        row -> (String) row.get("column_name"),
                                        row -> (String) row.get("data_type")));

        assertThat(columns)
                .containsEntry("id", "uuid")
                .containsEntry("actor_user_id", "uuid")
                .containsEntry("action", "character varying")
                .containsEntry("entity_type", "character varying")
                .containsEntry("entity_id", "uuid")
                .containsEntry("old_values", "jsonb")
                .containsEntry("new_values", "jsonb")
                .containsEntry("ip_address", "inet")
                .containsEntry("user_agent", "text")
                .containsEntry("created_at", "timestamp with time zone");
    }

    @Test
    void noColumnExistsBeyondTheSpecification() {
        // §20.1 is the whole contract. An actor_type discriminator was considered and rejected;
        // this fails if one is added without a specification amendment, and names the offender.
        List<String> columns =
                jdbcTemplate.queryForList(
                        "SELECT column_name FROM information_schema.columns"
                                + " WHERE table_schema = 'public' AND table_name = 'audit_logs'",
                        String.class);

        assertThat(columns)
                .containsExactlyInAnyOrder(
                        "id", "actor_user_id", "action", "entity_type", "entity_id",
                        "old_values", "new_values", "ip_address", "user_agent", "created_at");
    }

    @Test
    void actorIsNullableSoSystemActionsCanBeRecorded() {
        String nullable =
                jdbcTemplate.queryForObject(
                        "SELECT is_nullable FROM information_schema.columns"
                                + " WHERE table_schema = 'public' AND table_name = 'audit_logs'"
                                + " AND column_name = 'actor_user_id'",
                        String.class);

        assertThat(nullable).isEqualTo("YES");
    }

    @Test
    void bothPrescribedIndexesExist() {
        List<String> indexes =
                jdbcTemplate.queryForList(
                        "SELECT indexname FROM pg_indexes WHERE tablename = 'audit_logs'",
                        String.class);

        // Database Design §23, named per the §4 convention.
        assertThat(indexes)
                .contains(
                        "idx_audit_logs_entity_type_entity_id_created_at",
                        "idx_audit_logs_actor_user_id_created_at");
    }

    @Test
    void recordsCannotBeUpdated() {
        UUID id = recordedEventId();

        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "UPDATE audit_logs SET action = 'TAMPERED' WHERE id = ?",
                                        id))
                .hasMessageContaining("immutable");

        // Rejecting the statement is only half of it: the row must also be untouched.
        assertThat(actionOf(id)).isEqualTo("SCHEMA_PROBE");
    }

    @Test
    void recordsCannotBeDeleted() {
        UUID id = recordedEventId();

        assertThatThrownBy(
                        () -> jdbcTemplate.update("DELETE FROM audit_logs WHERE id = ?", id))
                .hasMessageContaining("immutable");

        assertThat(actionOf(id)).isEqualTo("SCHEMA_PROBE");
    }

    @Test
    void theTrailCannotBeTruncated() {
        // PostgreSQL never fires row-level triggers on TRUNCATE, so the row-level guard alone
        // would leave the entire trail erasable in a single statement.
        UUID id = recordedEventId();

        assertThatThrownBy(() -> jdbcTemplate.execute("TRUNCATE audit_logs"))
                .hasMessageContaining("immutable");

        assertThat(actionOf(id)).isEqualTo("SCHEMA_PROBE");
    }

    private String actionOf(UUID id) {
        return jdbcTemplate.queryForObject(
                "SELECT action FROM audit_logs WHERE id = ?", String.class, id);
    }

    /**
     * Commits a row so the immutability guards have something real to reject. The recorder is
     * {@code MANDATORY}, so it must be called inside a transaction — which is the point.
     */
    private UUID recordedEventId() {
        return new TransactionTemplate(transactionManager)
                .execute(
                        status ->
                                auditRecorder
                                        .record(
                                                AuditEvent.of(
                                                        AuditActor.system(),
                                                        "SCHEMA_PROBE",
                                                        "Probe",
                                                        UUID.randomUUID()))
                                        .getId());
    }
}
