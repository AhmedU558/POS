package com.pos.audit.service;

import com.pos.audit.domain.AuditEvent;
import com.pos.audit.domain.AuditLog;
import com.pos.audit.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * The one way to write an audit record.
 *
 * <p>Modules call this rather than touching the repository, so that every audited action goes
 * through the same validation and the same transaction semantics.
 */
@Service
public class AuditRecorder {

    private final AuditLogRepository auditLogRepository;

    public AuditRecorder(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Records an event inside the caller's transaction.
     *
     * <p>{@link Propagation#MANDATORY} is the decision, not a default. Database Design §25 places
     * {@code audit_logs} inside the transaction scope of the operations it records, so the audit
     * row and the action it describes must commit or roll back together. If the audited operation
     * fails, its audit record must not survive to claim it happened; if the audit write fails, the
     * operation must not proceed unrecorded, because AUD-001 makes logging a MUST.
     *
     * <p>{@code REQUIRED} was rejected: it silently starts a transaction when none is active, so a
     * caller outside one would commit the audit row independently — precisely the behaviour this
     * method exists to prevent, and invisible at the call site. {@code MANDATORY} turns that into
     * an immediate, loud failure instead.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public AuditLog record(AuditEvent event) {
        Objects.requireNonNull(event, "event");
        return auditLogRepository.save(AuditLog.from(event));
    }
}
