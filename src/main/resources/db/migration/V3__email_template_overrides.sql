-- V3: Email template overrides table
-- Replaces the old email_templates jsonb column in company_settings.
-- Only configurable templates are stored here — system templates are never overridden.
-- If no row exists for (tenant_id, template_key, locale) → default from code is used.

CREATE TABLE email_template_override (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    uuid        NOT NULL,
    template_key varchar(64) NOT NULL,
    locale       varchar(8)  NOT NULL,  -- 'uk' | 'en'
    subject      text        NOT NULL,
    body         text        NOT NULL,
    updated_by   uuid,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_override UNIQUE (tenant_id, template_key, locale)
);

CREATE INDEX idx_override_tenant ON email_template_override (tenant_id);

-- Migrate existing data from the old jsonb column.
-- Old keys: CONFIRMATION, REMINDER, AFTERCARE → new TemplateKey names: BOOKING_CONFIRMED, BOOKING_REMINDER, AFTERCARE_INSTRUCTIONS
-- Only rows that actually have overrides (jsonb entry is not null and not empty) are migrated.
INSERT INTO email_template_override (tenant_id, template_key, locale, subject, body, created_at, updated_at)
SELECT
    cs.tenant_id,
    CASE entry.key
        WHEN 'CONFIRMATION' THEN 'BOOKING_CONFIRMED'
        WHEN 'REMINDER'     THEN 'BOOKING_REMINDER'
        WHEN 'AFTERCARE'    THEN 'AFTERCARE_INSTRUCTIONS'
    END,
    'uk',
    COALESCE(entry.value ->> 'subject', ''),
    COALESCE(entry.value ->> 'body', ''),
    now(),
    now()
FROM company_settings cs,
     jsonb_each(cs.email_templates) AS entry(key, value)
WHERE cs.email_templates IS NOT NULL
  AND entry.key IN ('CONFIRMATION', 'REMINDER', 'AFTERCARE')
  AND (entry.value ->> 'subject' IS NOT NULL OR entry.value ->> 'body' IS NOT NULL)
ON CONFLICT (tenant_id, template_key, locale) DO NOTHING;

-- Drop the old column once data is migrated
ALTER TABLE company_settings DROP COLUMN IF EXISTS email_templates;

-- Extend email_logs with template_key and entity_id for new NotificationSender
ALTER TABLE email_logs
    ADD COLUMN IF NOT EXISTS template_key varchar(64),
    ADD COLUMN IF NOT EXISTS entity_id    uuid;

CREATE INDEX IF NOT EXISTS idx_email_log_template_entity
    ON email_logs (template_key, entity_id)
    WHERE template_key IS NOT NULL AND entity_id IS NOT NULL;
