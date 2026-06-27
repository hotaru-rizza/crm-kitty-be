ALTER TABLE appointments
    ADD COLUMN IF NOT EXISTS reservation BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE appointments
    ALTER COLUMN client_id DROP NOT NULL;

ALTER TABLE appointments
    ALTER COLUMN service_id DROP NOT NULL;

CREATE INDEX IF NOT EXISTS idx_appointment_reservation
    ON appointments (tenant_id, reservation)
    WHERE reservation = TRUE AND deleted_at IS NULL;
