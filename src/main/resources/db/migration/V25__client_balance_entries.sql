ALTER TABLE appointments
    ADD COLUMN IF NOT EXISTS balance_charged_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE IF NOT EXISTS client_balance_entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL,
    client_id       UUID         NOT NULL REFERENCES clients (id),
    amount          NUMERIC(12, 2) NOT NULL,
    reason          VARCHAR(32)  NOT NULL,
    appointment_id  UUID         REFERENCES appointments (id),
    transaction_id  UUID         REFERENCES transactions (id),
    note            TEXT,
    created_by      UUID,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE,
    updated_by      UUID,
    deleted_at      TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_client_balance_entries_client
    ON client_balance_entries (tenant_id, client_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_client_balance_entries_appointment
    ON client_balance_entries (tenant_id, appointment_id)
    WHERE appointment_id IS NOT NULL;
