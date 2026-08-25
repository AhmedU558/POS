-- Forced initial-password rotation (AMD-001 change A, approved 2026-08-25).
--
-- True means the account holds a credential it did not choose -- issued at bootstrap, or reset by
-- an administrator -- and may not be used until the holder replaces it. Cleared by a successful
-- password change and by nothing else.
--
-- Defaults false so the column is inert for every existing and future account unless something
-- deliberately sets it. Adding it changes no current behaviour.
ALTER TABLE users
    ADD COLUMN is_password_change_required BOOLEAN NOT NULL DEFAULT false;
