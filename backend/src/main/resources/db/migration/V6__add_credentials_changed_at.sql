-- Story 1.4: Strict token revocation semantics
-- Add a timestamp anchored to the moment the credential was last changed.
-- A token issued before this timestamp is invalid, closing the leaked-bootstrap loophole.

ALTER TABLE users 
ADD COLUMN credentials_changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
