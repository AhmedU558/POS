package com.pos.audit.repository;

import com.pos.audit.domain.AuditLog;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Access to the audit trail.
 *
 * <p><strong>Extends {@link Repository}, not {@code JpaRepository}, deliberately.</strong>
 * {@code JpaRepository} would inherit {@code delete}, {@code deleteById} and {@code deleteAll},
 * putting a one-line route to destroying audit history within reach of any caller. SRS AUD-003
 * forbids that, so the surface is declared explicitly and simply has no such method.
 *
 * <p>There is also no update path: {@link AuditLog} has no setters, so a caller holding a managed
 * instance has nothing to change. The database rejects updates and deletes as a third line of
 * defence.
 *
 * <p>Only lookups this story actually uses are declared. Read endpoints (REST API Specification
 * §25) arrive with the story that can authenticate and authorise them.
 */
public interface AuditLogRepository extends Repository<AuditLog, UUID> {

    AuditLog save(AuditLog auditLog);

    Optional<AuditLog> findById(UUID id);

    /** Entity audit history, matching the index prescribed by Database Design §23. */
    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, UUID entityId);
}
