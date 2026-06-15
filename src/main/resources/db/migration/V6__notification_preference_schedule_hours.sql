ALTER TABLE notification_preference ADD COLUMN IF NOT EXISTS schedule_hours INTEGER;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'notification_preference'
          AND column_name = 'schedule_params'
    ) THEN
        UPDATE notification_preference
        SET schedule_hours = COALESCE(
            (schedule_params->>'hoursBefore')::int,
            (schedule_params->>'hoursAfterDone')::int
        )
        WHERE schedule_params IS NOT NULL;

        ALTER TABLE notification_preference DROP COLUMN schedule_params;
    END IF;
END $$;
