package com.pos.audit;

import com.pos.AbstractIntegrationTest;
import com.pos.audit.domain.AuditActor;
import com.pos.audit.domain.AuditEvent;
import com.pos.audit.domain.AuditLog;
import com.pos.audit.domain.AuditRequestContext;
import com.pos.audit.repository.AuditLogRepository;
import com.pos.audit.service.AuditRecorder;
import com.pos.users.domain.User;
import com.pos.users.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies what the recorder writes, and that a SYSTEM actor is representable without any human
 * principal — the property Story 1.2 bootstrap depends on.
 */
@Transactional
class AuditRecorderTests extends AbstractIntegrationTest {

    @Autowired private AuditRecorder auditRecorder;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    @Test
    void systemActionsAreRecordedWithNoActorAndNoUsersInTheDatabase() {
        // Recreate the exact condition at bootstrap: not a single user row exists, so there is no
        // principal any audit record could name. Emptied inside this transaction so the rollback
        // restores whatever other tests left behind -- asserting on the ambient count instead
        // would make this pass or fail on test ordering.
        jdbcTemplate.update("DELETE FROM user_roles");
        jdbcTemplate.update("DELETE FROM users");
        assertThat(userRepository.count()).isZero();

        AuditLog recorded =
                auditRecorder.record(
                        AuditEvent.of(AuditActor.system(), "BOOTSTRAP_PROBE", "System", null));
        entityManager.flush();

        assertThat(recorded.getActorUserId()).isNull();
        assertThat(recorded.isSystemInitiated()).isTrue();
        assertThat(recorded.getCreatedAt()).isNotNull();
    }

    @Test
    void humanActionsAreRecordedAgainstTheActingUser() {
        User actor = persistedUser("audit.actor");

        AuditLog recorded =
                auditRecorder.record(
                        AuditEvent.of(
                                AuditActor.user(actor.getId()),
                                "USER_UPDATED",
                                "User",
                                actor.getId()));

        assertThat(recorded.getActorUserId()).isEqualTo(actor.getId());
        assertThat(recorded.isSystemInitiated()).isFalse();
    }

    @Test
    void anActorThatDoesNotExistIsRejectedByTheDatabase() {
        AuditEvent event =
                AuditEvent.of(AuditActor.user(UUID.randomUUID()), "GHOST", "User", null);

        // The foreign key is checked when the INSERT runs, which is at flush -- not at save().
        // Flushing through the EntityManager bypasses Spring's exception translation, so the
        // assertion is on the constraint that fired rather than on which wrapper carried it.
        assertThatThrownBy(
                        () -> {
                            auditRecorder.record(event);
                            entityManager.flush();
                        })
                .isInstanceOfAny(
                        DataIntegrityViolationException.class, ConstraintViolationException.class)
                .hasMessageContaining("audit_logs_actor_user_id_fkey");
    }

    @Test
    void structuredValuesRoundTripThroughJsonb() {
        String before = "{\"price\":\"10.00\",\"active\":true}";
        String after = "{\"price\":\"12.50\",\"active\":true}";

        AuditLog recorded =
                auditRecorder.record(
                        new AuditEvent(
                                AuditActor.system(),
                                "PRICE_UPDATED",
                                "Product",
                                UUID.randomUUID(),
                                before,
                                after,
                                AuditRequestContext.none()));

        // Flush and clear so the reload genuinely comes back through jsonb rather than from the
        // persistence context, which would return the same String regardless of the column type.
        entityManager.flush();
        entityManager.clear();

        AuditLog reloaded = auditLogRepository.findById(recorded.getId()).orElseThrow();
        assertThat(reloaded.getOldValues()).contains("10.00");
        assertThat(reloaded.getNewValues()).contains("12.50");
    }

    @Test
    void requestContextIsStoredWithTheIpAsANativeInet() {
        AuditLog recorded =
                auditRecorder.record(
                        new AuditEvent(
                                AuditActor.system(),
                                "LOGIN_PROBE",
                                "User",
                                null,
                                null,
                                null,
                                AuditRequestContext.of("203.0.113.42", "probe/1.0")));
        entityManager.flush();

        // Read back through SQL to prove the value landed in an inet column, not merely a string
        // the JVM happened to hand back unchanged.
        String storedType =
                jdbcTemplate.queryForObject(
                        "SELECT pg_typeof(ip_address)::text FROM audit_logs WHERE id = ?",
                        String.class,
                        recorded.getId());

        assertThat(storedType).isEqualTo("inet");
        assertThat(recorded.getUserAgent()).isEqualTo("probe/1.0");
    }

    @Test
    void systemActionsCarryNoRequestContext() {
        AuditLog recorded =
                auditRecorder.record(
                        AuditEvent.of(AuditActor.system(), "SCHEDULED", "System", null));

        assertThat(recorded.getIpAddress()).isNull();
        assertThat(recorded.getUserAgent()).isNull();
    }

    @Test
    void blankActionOrEntityTypeIsRejectedBeforeAnyDatabaseCall() {
        assertThatThrownBy(() -> AuditEvent.of(AuditActor.system(), "  ", "User", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("action");

        assertThatThrownBy(() -> AuditEvent.of(AuditActor.system(), "ACTION", "", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entityType");
    }

    @Test
    void oversizedActionIsRejectedRatherThanTruncatedByTheDatabase() {
        String tooLong = "X".repeat(101);

        assertThatThrownBy(() -> AuditEvent.of(AuditActor.system(), tooLong, "User", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anEventCannotBeBuiltWithoutChoosingAnActor() {
        assertThatThrownBy(() -> AuditEvent.of(null, "ACTION", "User", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void theRepositoryExposesNoWayToDeleteAuditHistory() {
        // AUD-003 enforced at the application boundary as well as in the database. Extending
        // JpaRepository would silently reintroduce delete, deleteById and deleteAll.
        var methodNames =
                Arrays.stream(AuditLogRepository.class.getMethods()).map(Method::getName).toList();

        assertThat(methodNames).noneMatch(name -> name.toLowerCase().contains("delete"));
        assertThat(methodNames).noneMatch(name -> name.toLowerCase().contains("remove"));
    }

    @Test
    void entityHistoryComesBackNewestFirstAndFilteredByType() throws InterruptedException {
        UUID entityId = UUID.randomUUID();
        auditRecorder.record(AuditEvent.of(AuditActor.system(), "CREATED", "Product", entityId));
        entityManager.flush();
        // The database assigns created_at to microsecond precision; without a gap the two rows can
        // share a timestamp and the ordering this method promises becomes unobservable.
        Thread.sleep(5);
        auditRecorder.record(AuditEvent.of(AuditActor.system(), "UPDATED", "Product", entityId));
        // Same id under a different type, so the entity_type predicate is actually exercised.
        auditRecorder.record(AuditEvent.of(AuditActor.system(), "DECOY", "Supplier", entityId));
        entityManager.flush();

        assertThat(
                        auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                                "Product", entityId))
                .extracting(AuditLog::getAction)
                .containsExactly("UPDATED", "CREATED");
    }

    @Test
    void malformedJsonIsRejectedBeforeItCanAbortTheAuditedOperation() {
        // A jsonb cast error at flush would roll back the business transaction, not just the
        // audit row -- the caller's operation would fail for a reason it never caused.
        assertThatThrownBy(
                        () ->
                                new AuditEvent(
                                        AuditActor.system(),
                                        "PRICE_UPDATED",
                                        "Product",
                                        null,
                                        "not json at all",
                                        null,
                                        AuditRequestContext.none()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("oldValues");
    }

    @Test
    void aMalformedClientAddressIsDiscardedRatherThanThrown() {
        // X-Forwarded-For chains, "unknown", and host:port all appear in the wild. None may abort
        // the operation being audited, so the value is dropped instead.
        assertThat(AuditRequestContext.of("203.0.113.7, 198.51.100.2", "ua").ipAddress()).isNull();
        assertThat(AuditRequestContext.of("unknown", "ua").ipAddress()).isNull();
        assertThat(AuditRequestContext.of("203.0.113.7", "ua").ipAddress()).isEqualTo("203.0.113.7");
    }

    @Test
    void anOversizedUserAgentIsBoundedBeforeStorage() {
        // The only fully attacker-controlled field, landing in an unbounded TEXT column that
        // nothing is permitted to delete.
        String hostile = "U".repeat(10_000);

        assertThat(AuditRequestContext.of("203.0.113.7", hostile).userAgent()).hasSize(512);
    }

    @Test
    void aNullRequestContextNormalisesRatherThanReachingTheEntity() {
        AuditEvent event =
                new AuditEvent(AuditActor.system(), "ACTION", "User", null, null, null, null);

        assertThat(event.requestContext()).isEqualTo(AuditRequestContext.none());
    }

    @Test
    void oversizedEntityTypeIsRejectedToo() {
        assertThatThrownBy(
                        () -> AuditEvent.of(AuditActor.system(), "ACTION", "X".repeat(101), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entityType");
    }

    @Test
    void actionAndEntityTypeAreTrimmedSoOneActionHasOneSpelling() {
        AuditEvent event = AuditEvent.of(AuditActor.system(), "  CREATED  ", "  Product  ", null);

        assertThat(event.action()).isEqualTo("CREATED");
        assertThat(event.entityType()).isEqualTo("Product");
    }

    @Test
    void theEventDescriptionNeverCarriesTheValuePayloads() {
        // A record's generated toString would put these into any exception message or debug log,
        // where retention and access control are weaker than on audit_logs itself.
        AuditEvent event =
                new AuditEvent(
                        AuditActor.system(),
                        "PRICE_UPDATED",
                        "Product",
                        null,
                        "{\"secret\":\"do-not-log-me\"}",
                        null,
                        AuditRequestContext.none());

        assertThat(event.toString()).doesNotContain("do-not-log-me").contains("PRICE_UPDATED");
    }

    @Test
    void recordingRequiresAnEvent() {
        assertThatThrownBy(() -> auditRecorder.record(null))
                .isInstanceOf(NullPointerException.class);
    }

    private User persistedUser(String username) {
        return userRepository.saveAndFlush(
                new User(username, "hashed-value-must-not-leak", "Audit", "Actor"));
    }
}
