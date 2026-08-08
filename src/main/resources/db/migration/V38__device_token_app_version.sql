ALTER TABLE device_tokens
    ADD COLUMN IF NOT EXISTS app_version VARCHAR(64);
