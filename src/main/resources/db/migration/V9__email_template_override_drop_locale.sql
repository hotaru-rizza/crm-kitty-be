-- V9: Templates are now language-agnostic — one override row per (tenant, template_key).
-- Collapse multi-locale rows: prefer 'uk', then keep lowest id among remaining duplicates.

BEGIN;

-- Prefer 'uk' row: delete non-uk duplicates where a uk row exists for the same (tenant, key).
DELETE FROM email_template_override e
USING email_template_override keep
WHERE e.tenant_id    = keep.tenant_id
  AND e.template_key = keep.template_key
  AND keep.locale    = 'uk'
  AND e.locale      <> 'uk';

-- For any remaining groups still with duplicates (no uk row existed), keep the lowest id.
DELETE FROM email_template_override e
USING email_template_override other
WHERE e.tenant_id    = other.tenant_id
  AND e.template_key = other.template_key
  AND e.id > other.id;

ALTER TABLE email_template_override DROP CONSTRAINT IF EXISTS uq_override;
ALTER TABLE email_template_override DROP COLUMN locale;
ALTER TABLE email_template_override
    ADD CONSTRAINT uq_override_tenant_key UNIQUE (tenant_id, template_key);

COMMIT;
