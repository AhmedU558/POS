package com.pos.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A persisted audit record.
 *
 * <p>Maps the {@code audit_logs} table from Database Design & ERD Specification section 20.1.
 *
 * <p><strong>Immutable by construction.</strong> There are no setters and no public constructor
 * that mutates an existing row; SRS AUD-003 forbids altering audit records, and the database
 * enforces the same rule with a trigger. Both exist because either alone can be bypassed — the
 * trigger by code holding a raw connection, the entity by a future author adding a setter.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The user who acted, or {@code null} for a system-initiated action.
     *
     * <p>Deliberately a plain identifier rather than a {@code @ManyToOne} to {@code User}. An audit
     * record is a historical statement, not a live view of an account: resolving it to an entity
     * would invite lazy loading on every read and would tie the audit module to the identity
     * module's mapping.
     */
    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_values")
    private String oldValues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_values")
    private String newValues;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    /**
     * Assigned by the database, not the JVM.
     *
     * <p>The Architecture Document requires horizontal scaling, so application clocks can skew
     * between instances. An audit trail whose value rests on temporal ordering must not record a
     * time that depends on which node served the request, and these rows can never be corrected
     * afterwards.
     */
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    /** Required by JPA. */
    protected AuditLog() {}

    private AuditLog(AuditEvent event) {
        this.actorUserId = event.actor().persistedUserId();
        this.action = event.action();
        this.entityType = event.entityType();
        this.entityId = event.entityId();
        this.oldValues = event.oldValues();
        this.newValues = event.newValues();
        this.ipAddress = event.requestContext().ipAddress();
        this.userAgent = event.requestContext().userAgent();
    }

    /** The only way to create an audit record: from a validated event. */
    public static AuditLog from(AuditEvent event) {
        return new AuditLog(event);
    }

    public UUID getId() {
        return id;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    /** True when no human principal performed this action. */
    public boolean isSystemInitiated() {
        return actorUserId == null;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getOldValues() {
        return oldValues;
    }

    public String getNewValues() {
        return newValues;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "AuditLog[" + action + " on " + entityType + " at " + createdAt + "]";
    }
}
