ALTER TABLE company_settings
    DROP COLUMN IF EXISTS sms_reminders,
    DROP COLUMN IF EXISTS telegram_reminders,
    DROP COLUMN IF EXISTS email_reminders,
    DROP COLUMN IF EXISTS email_confirmations,
    DROP COLUMN IF EXISTS email_aftercare,
    DROP COLUMN IF EXISTS email_cancellation,
    DROP COLUMN IF EXISTS email_reschedule,
    DROP COLUMN IF EXISTS email_staff_new_appointment,
    DROP COLUMN IF EXISTS email_staff_cancellation,
    DROP COLUMN IF EXISTS email_staff_reschedule;
