-- First-administrator provisioning marker (AMD-001 change B, approved 2026-08-25).
--
-- Records that bootstrap has completed. The guard for first-administrator provisioning is
-- "bootstrap has never completed", NOT "no administrator exists": inferring intent from absent
-- state means one deletion -- a support error, a restore from backup, an attacker with a single
-- write -- would make the next restart rebuild an administrator using the operator's original,
-- long-since-leaked password. See docs/adr/ADR-015-first-administrator-provisioning.md.
--
-- The security properties are carried by constraints rather than by application logic, so they
-- survive any later refactor of the bootstrap code:
--
--   * Permanently one-shot -- UNIQUE (is_singleton) with the CHECK permits at most one row ever,
--     so a second insert is rejected by PostgreSQL and no code path can bypass it.
--   * No resurrection      -- ON DELETE SET NULL releases the foreign key instead of cascading,
--     so the marker outlives the account it created. administrator_username keeps the forensic
--     record after the account is gone.
--   * Concurrency-safe     -- when instances start together both attempt the insert and the
--     database picks the winner. No advisory lock and no distributed lock are needed.
CREATE TABLE bootstrap_completions (
    id UUID PRIMARY KEY,
    is_singleton BOOLEAN NOT NULL DEFAULT true,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    administrator_user_id UUID
        CONSTRAINT bootstrap_completions_administrator_user_id_fkey
        REFERENCES users(id) ON DELETE SET NULL,
    administrator_username VARCHAR(100) NOT NULL,
    CONSTRAINT uk_bootstrap_completions_singleton UNIQUE (is_singleton),
    CONSTRAINT ck_bootstrap_completions_singleton CHECK (is_singleton IS TRUE)
);
