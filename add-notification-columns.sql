-- Add new notification columns to company_settings
ALTER TABLE company_settings
    ADD COLUMN IF NOT EXISTS email_cancellation BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS email_reschedule BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS email_staff_new_appointment BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS email_staff_cancellation BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS email_staff_reschedule BOOLEAN NOT NULL DEFAULT FALSE;
