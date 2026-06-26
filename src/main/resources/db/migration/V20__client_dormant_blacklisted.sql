ALTER TABLE clients
    ADD COLUMN IF NOT EXISTS dormant BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS blacklisted BOOLEAN NOT NULL DEFAULT false;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'clients'
          AND column_name = 'status'
    ) THEN
        UPDATE clients SET blacklisted = true WHERE status = 'BLACKLISTED';
        UPDATE clients SET dormant = true WHERE status = 'INACTIVE';

        ALTER TABLE clients DROP CONSTRAINT IF EXISTS clients_status_check;
        ALTER TABLE clients DROP COLUMN status;
    END IF;
END $$;

ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS client_dormancy_days INTEGER NOT NULL DEFAULT 90;

ALTER TABLE requests
    ADD COLUMN IF NOT EXISTS email VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_client_email_tenant
    ON clients (tenant_id, lower(email))
    WHERE deleted_at IS NULL AND email IS NOT NULL AND email <> '';
