ALTER TABLE clients ADD COLUMN IF NOT EXISTS whatsapp VARCHAR(255);
ALTER TABLE clients ADD COLUMN IF NOT EXISTS facebook VARCHAR(255);
ALTER TABLE clients ADD COLUMN IF NOT EXISTS balance NUMERIC(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE clients ADD COLUMN IF NOT EXISTS first_visit TIMESTAMP WITH TIME ZONE;

UPDATE clients c
SET first_visit = (
    SELECT MIN(a.start_time)
    FROM appointments a
    WHERE a.client_id = c.id
      AND a.deleted_at IS NULL
)
WHERE c.deleted_at IS NULL
  AND c.first_visit IS NULL;
