-- V10: Email template constructor (idempotent).
-- Safe on: fresh DB, legacy email_logs, partial Hibernate state, re-run.

CREATE TABLE IF NOT EXISTS email_template (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    trigger_type    VARCHAR(40) NOT NULL,
    offset_minutes  INTEGER,
    subject         VARCHAR(255) NOT NULL,
    body            TEXT NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    deletable       BOOLEAN NOT NULL DEFAULT TRUE,
    builtin_key     VARCHAR(64),
    category        VARCHAR(20) NOT NULL,
    updated_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_email_template_tenant_builtin UNIQUE (tenant_id, builtin_key)
);

CREATE INDEX IF NOT EXISTS idx_email_template_tenant_trigger_enabled
    ON email_template (tenant_id, trigger_type, enabled);

-- email_logs → email_message, or create email_message if neither exists
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'email_logs'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'email_message'
    ) THEN
        ALTER TABLE email_logs RENAME TO email_message;
    ELSIF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'email_message'
    ) THEN
        CREATE TABLE email_message (
            id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            tenant_id        UUID NOT NULL,
            template_id      UUID REFERENCES email_template(id),
            trigger_type     VARCHAR(40) NOT NULL DEFAULT 'MANUAL',
            recipient_email  VARCHAR(255) NOT NULL,
            recipient_name   VARCHAR(255),
            subject          VARCHAR(255) NOT NULL,
            body             TEXT NOT NULL DEFAULT '',
            status           VARCHAR(16) NOT NULL DEFAULT 'SENT',
            attempts         INTEGER NOT NULL DEFAULT 0,
            next_attempt_at  TIMESTAMPTZ,
            last_error       VARCHAR(512),
            entity_id        UUID,
            dedupe_key       VARCHAR(128),
            created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
            sent_at          TIMESTAMPTZ
        );
    END IF;
END $$;

ALTER TABLE email_message
    ADD COLUMN IF NOT EXISTS template_id UUID REFERENCES email_template(id),
    ADD COLUMN IF NOT EXISTS trigger_type VARCHAR(40),
    ADD COLUMN IF NOT EXISTS body TEXT,
    ADD COLUMN IF NOT EXISTS attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS dedupe_key VARCHAR(128),
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS entity_id UUID;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'email_message' AND column_name = 'error_message'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'email_message' AND column_name = 'last_error'
    ) THEN
        ALTER TABLE email_message RENAME COLUMN error_message TO last_error;
    END IF;
END $$;

ALTER TABLE email_message
    ADD COLUMN IF NOT EXISTS last_error VARCHAR(512);

-- Migrate template_key → trigger_type (only when legacy column still present)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'email_message' AND column_name = 'template_key'
    ) THEN
        UPDATE email_message
        SET trigger_type = CASE template_key
            WHEN 'BOOKING_CONFIRMED' THEN 'BOOKING_CONFIRMED'
            WHEN 'BOOKING_CANCELED' THEN 'BOOKING_CANCELED'
            WHEN 'BOOKING_RESCHEDULED' THEN 'BOOKING_RESCHEDULED'
            WHEN 'BOOKING_REMINDER' THEN 'BEFORE_BOOKING'
            WHEN 'AFTERCARE_INSTRUCTIONS' THEN 'AFTER_BOOKING'
            WHEN 'PREP_INSTRUCTIONS' THEN 'BEFORE_BOOKING'
            WHEN 'REVIEW_REQUEST' THEN 'AFTER_BOOKING'
            WHEN 'BIRTHDAY' THEN 'CLIENT_BIRTHDAY'
            WHEN 'WINBACK' THEN 'CLIENT_INACTIVE'
            WHEN 'BULK_EMAIL' THEN 'MANUAL'
            ELSE 'MANUAL'
        END
        WHERE trigger_type IS NULL;

        ALTER TABLE email_message DROP COLUMN template_key;
    END IF;
END $$;

UPDATE email_message SET trigger_type = 'MANUAL' WHERE trigger_type IS NULL;
UPDATE email_message SET body = '' WHERE body IS NULL;
UPDATE email_message SET created_at = COALESCE(sent_at, now()) WHERE created_at IS NULL;
UPDATE email_message SET attempts = 0 WHERE attempts IS NULL;
UPDATE email_message SET status = 'SENT' WHERE status IS NULL;

ALTER TABLE email_message ALTER COLUMN trigger_type SET DEFAULT 'MANUAL';
ALTER TABLE email_message ALTER COLUMN trigger_type SET NOT NULL;
ALTER TABLE email_message ALTER COLUMN body SET DEFAULT '';
ALTER TABLE email_message ALTER COLUMN body SET NOT NULL;
ALTER TABLE email_message ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE email_message ALTER COLUMN created_at SET NOT NULL;

ALTER TABLE email_message ALTER COLUMN sent_at DROP NOT NULL;

ALTER TABLE email_message DROP CONSTRAINT IF EXISTS email_logs_status_check;
ALTER TABLE email_message DROP CONSTRAINT IF EXISTS email_message_status_check;

CREATE UNIQUE INDEX IF NOT EXISTS uq_email_message_dedupe_key
    ON email_message (dedupe_key) WHERE dedupe_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_email_message_status_next_attempt
    ON email_message (status, next_attempt_at);

CREATE INDEX IF NOT EXISTS idx_email_message_tenant_created
    ON email_message (tenant_id, created_at);

DROP TABLE IF EXISTS notification_preference;
DROP TABLE IF EXISTS email_template_override;
