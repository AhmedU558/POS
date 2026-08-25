package com.pos.audit.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Who performed an audited action.
 *
 * <p>Sealed so a caller must choose between a human principal and the system itself. That choice
 * is the whole point: {@code audit_logs.actor_user_id} is NULL for system-initiated actions, and
 * without an explicit type a NULL would be indistinguishable from a caller who simply forgot to
 * set an actor. Here, NULL is only reachable by asking for {@link #system()}.
 *
 * <p>See {@code docs/adr/ADR-016-system-actor-convention.md}.
 */
public sealed interface AuditActor {

    /** Allocated once: the system actor carries no state. */
    SystemProcess SYSTEM = new SystemProcess();

    /** A named user performed the action. */
    record Human(UUID userId) implements AuditActor {
        public Human {
            Objects.requireNonNull(userId, "userId");
        }
    }

    /**
     * The application performed the action with no human principal — startup provisioning,
     * scheduled work, or an integration callback.
     */
    record SystemProcess() implements AuditActor {}

    static AuditActor user(UUID userId) {
        return new Human(userId);
    }

    static AuditActor system() {
        return SYSTEM;
    }

    /**
     * The value to persist: the user's id, or {@code null} for a system action.
     *
     * <p>An exhaustive switch, not an {@code instanceof} chain. Adding a third permitted actor
     * kind must then fail to compile here, rather than silently falling through to null and
     * becoming indistinguishable from a system action in the trail.
     */
    default UUID persistedUserId() {
        return switch (this) {
            case Human human -> human.userId();
            case SystemProcess ignored -> null;
        };
    }
}
