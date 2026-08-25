package com.pos.bootstrap.domain;

import com.pos.users.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.util.UUID;

/**
 * Records that first-administrator provisioning has completed.
 *
 * <p>At most one row can ever exist: {@code is_singleton} is UNIQUE and constrained to true, so
 * PostgreSQL rejects a second insert. That constraint, not any application check, is what makes
 * bootstrap permanently one-shot and what arbitrates a race between instances starting together.
 *
 * <p>The row outlives the administrator it created. Deleting that account sets
 * {@code administrator_user_id} to null rather than removing the marker, so a later restart still
 * finds bootstrap already completed and does not rebuild the account.
 */
@Entity
@Table(name = "bootstrap_completions")
public class BootstrapCompletion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "is_singleton", nullable = false)
    private boolean singleton = true;

    @Generated(event = EventType.INSERT)
    @Column(name = "completed_at", nullable = false, insertable = false, updatable = false)
    private Instant completedAt;

    @Column(name = "administrator_user_id")
    private UUID administratorUserId;

    /** Survives deletion of the account, keeping the forensic record intact. */
    @Column(name = "administrator_username", nullable = false, length = 100)
    private String administratorUsername;

    /** Required by JPA. */
    protected BootstrapCompletion() {}

    /**
     * Claims the singleton for the named administrator, before that account exists.
     *
     * <p>The marker is written first, deliberately. Whichever constraint fires first is the one
     * that decides the outcome, and only this table's constraint means "provisioning has already
     * happened" — a clash on {@code users.username} could equally mean an unrelated account holds
     * the configured name, which is a genuine failure rather than a benign race.
     */
    public static BootstrapCompletion claimFor(String administratorUsername) {
        BootstrapCompletion completion = new BootstrapCompletion();
        completion.administratorUsername = administratorUsername;
        return completion;
    }

    /**
     * Attaches the account once it has been persisted and has an identifier.
     *
     * <p>Only meaningful inside the transaction that created this row; the foreign key is nullable
     * precisely so the marker can be claimed before the account exists, and can outlive it
     * afterwards.
     */
    public void linkAdministrator(User administrator) {
        this.administratorUserId = administrator.getId();
    }

    public UUID getId() {
        return id;
    }

    public boolean isSingleton() {
        return singleton;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public UUID getAdministratorUserId() {
        return administratorUserId;
    }

    public String getAdministratorUsername() {
        return administratorUsername;
    }

    @Override
    public String toString() {
        return "BootstrapCompletion[" + administratorUsername + " at " + completedAt + "]";
    }
}
