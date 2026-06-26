-- Email becomes required; phone becomes optional.
-- Backfill legacy rows before NOT NULL on email.

UPDATE clients
SET email = 'legacy+' || id::text || '@no-email.local'
WHERE deleted_at IS NULL
  AND (email IS NULL OR btrim(email) = '');

-- Clear auto-generated phone placeholders from request conversion (np + 32 hex chars).
UPDATE clients
SET phone = NULL
WHERE deleted_at IS NULL
  AND phone ~ '^np[0-9a-f]{32}$';

ALTER TABLE clients
    ALTER COLUMN email SET NOT NULL;

ALTER TABLE clients
    ALTER COLUMN phone DROP NOT NULL;
