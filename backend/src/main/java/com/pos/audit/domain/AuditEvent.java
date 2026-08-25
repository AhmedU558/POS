package com.pos.audit.domain;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/**
 * A privileged or financially significant action to be recorded.
 *
 * <p>SRS AUD-002 requires an actor, action, target and timestamp. Actor and action are mandatory
 * here; the timestamp is assigned on write; the target is optional because some actions have no
 * single row to point at.
 *
 * <p>{@code action} is a plain string rather than a shared enum. Audit actions belong to the
 * modules that emit them, and a central catalogue would couple every module to this one.
 */
public record AuditEvent(
        AuditActor actor,
        String action,
        String entityType,
        UUID entityId,
        String oldValues,
        String newValues,
        AuditRequestContext requestContext) {

    private static final int ACTION_MAX_LENGTH = 100;
    private static final int ENTITY_TYPE_MAX_LENGTH = 100;
    private static final JsonFactory JSON = new JsonFactory();

    public AuditEvent {
        Objects.requireNonNull(actor, "actor");
        action = requireUsable(action, "action", ACTION_MAX_LENGTH);
        entityType = requireUsable(entityType, "entityType", ENTITY_TYPE_MAX_LENGTH);
        requireJsonOrNull(oldValues, "oldValues");
        requireJsonOrNull(newValues, "newValues");
        requestContext = requestContext == null ? AuditRequestContext.none() : requestContext;
    }

    /** An action with no structured before/after state and no request behind it. */
    public static AuditEvent of(AuditActor actor, String action, String entityType, UUID entityId) {
        return new AuditEvent(
                actor, action, entityType, entityId, null, null, AuditRequestContext.none());
    }

    /**
     * Rejects blank values at construction rather than letting them reach the database, where the
     * failure would surface as a constraint violation far from the caller that caused it.
     */
    private static String requireUsable(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(
                    "%s must not exceed %d characters".formatted(field, maxLength));
        }
        // Trimmed so the same logical action cannot persist under several spellings and silently
        // split any later grouping or filtering.
        return trimmed;
    }

    /**
     * Rejects malformed JSON here rather than at flush.
     *
     * <p>These land in {@code jsonb} columns, and {@link com.pos.audit.service.AuditRecorder}
     * joins the caller's transaction by design — so a bad payload would not merely fail to be
     * audited, it would roll back the business operation it was describing.
     */
    private static void requireJsonOrNull(String value, String field) {
        if (value == null) {
            return;
        }
        try (JsonParser parser = JSON.createParser(value)) {
            while (parser.nextToken() != null) {
                // Consume the document; malformed input throws.
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException(field + " must be valid JSON", ex);
        }
    }

    /**
     * Excludes the value payloads.
     *
     * <p>A record's generated {@code toString} would put {@code oldValues} and {@code newValues}
     * into any exception message or debug log, where retention and access control are weaker than
     * on {@code audit_logs} itself.
     */
    @Override
    public String toString() {
        return "AuditEvent[" + action + " on " + entityType + "]";
    }
}
