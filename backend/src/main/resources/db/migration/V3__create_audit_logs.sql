-- Audit trail.
--
-- Columns follow Database Design & ERD Specification section 20.1 exactly. Indexes follow
-- section 23. No column is added beyond the specification: representing a system-initiated
-- action is handled by leaving actor_user_id NULL, which section 20.1 permits.
-- See docs/adr/ADR-016-system-actor-convention.md.

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    -- NULL means system-initiated: there is no human principal. No ON DELETE clause, so a user
    -- carrying audit history cannot be deleted -- the trail pins the actor it names.
    actor_user_id UUID CONSTRAINT audit_logs_actor_user_id_fkey REFERENCES users(id),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID,
    old_values JSONB,
    new_values JSONB,
    ip_address INET,
    user_agent TEXT,
    -- Assigned by the database. Application clocks can skew across horizontally scaled
    -- instances; the trail's ordering must not depend on which node served the request.
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Section 23: entity audit history, and user activity auditing.
CREATE INDEX idx_audit_logs_entity_type_entity_id_created_at
    ON audit_logs (entity_type, entity_id, created_at);
CREATE INDEX idx_audit_logs_actor_user_id_created_at
    ON audit_logs (actor_user_id, created_at);

-- SRS AUD-003: normal users must not be able to alter or delete audit records, and Database
-- Design section 28 asks for restricted modification rights on audit tables. Enforcing this in
-- the database makes it structural rather than a convention the next author can overlook.
--
-- A controlled retention or archival job is expected to disable this trigger deliberately; that
-- is a privileged database operation, not something the application can reach.
CREATE OR REPLACE FUNCTION reject_audit_log_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs rows are immutable (SRS AUD-003): % rejected', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_logs_immutable
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION reject_audit_log_mutation();

-- PostgreSQL never fires FOR EACH ROW triggers on TRUNCATE, and a row-level trigger cannot be
-- declared for it at all. Without this statement-level guard the entire trail is erasable in one
-- statement while the row-level trigger above still reports the table as protected.
CREATE TRIGGER trg_audit_logs_no_truncate
    BEFORE TRUNCATE ON audit_logs
    FOR EACH STATEMENT EXECUTE FUNCTION reject_audit_log_mutation();
