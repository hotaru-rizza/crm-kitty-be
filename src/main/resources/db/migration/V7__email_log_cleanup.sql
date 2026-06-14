-- V7: Clean up email_logs — replace legacy type/appointment_id with template_key/entity_id.
-- V3 already added template_key and entity_id columns (nullable).
-- This migration backfills template_key from the old type column, enforces NOT NULL, then drops legacy columns.

BEGIN;

-- Backfill template_key where it is still NULL (rows written before V3 notificationSender was deployed).
-- 'CONFIRMATION' → BOOKING_CONFIRMED, 'REMINDER' → BOOKING_REMINDER, 'AFTERCARE' → AFTERCARE_INSTRUCTIONS, 'MANUAL' → BULK_EMAIL.
UPDATE email_logs
SET template_key = CASE type
    WHEN 'CONFIRMATION' THEN 'BOOKING_CONFIRMED'
    WHEN 'REMINDER'     THEN 'BOOKING_REMINDER'
    WHEN 'AFTERCARE'    THEN 'AFTERCARE_INSTRUCTIONS'
    WHEN 'MANUAL'       THEN 'BULK_EMAIL'
    ELSE type
END
WHERE template_key IS NULL AND type IS NOT NULL;

-- entity_id: backfill from appointment_id where entity_id is still NULL.
UPDATE email_logs
SET entity_id = appointment_id
WHERE entity_id IS NULL AND appointment_id IS NOT NULL;

-- Enforce NOT NULL now that backfill is complete.
ALTER TABLE email_logs ALTER COLUMN template_key SET NOT NULL;

-- Drop legacy columns.
ALTER TABLE email_logs DROP COLUMN IF EXISTS type;
ALTER TABLE email_logs DROP COLUMN IF EXISTS appointment_id;

COMMIT;
