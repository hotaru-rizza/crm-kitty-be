ALTER TABLE audit_log
    ADD COLUMN IF NOT EXISTS subject_client_id uuid NULL;

CREATE INDEX IF NOT EXISTS idx_audit_subject_client
    ON audit_log (tenant_id, subject_client_id);
