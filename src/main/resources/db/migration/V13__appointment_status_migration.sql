ALTER TABLE appointments
    DROP CONSTRAINT IF EXISTS appointments_status_check;

UPDATE appointments SET status = 'SCHEDULED' WHERE status IN ('NEW', 'CONFIRMED', 'IN_PROGRESS');
UPDATE appointments SET status = 'COMPLETED' WHERE status = 'DONE';

ALTER TABLE appointments
    ADD CONSTRAINT appointments_status_check
        CHECK (status IN ('SCHEDULED', 'COMPLETED', 'CANCELLED', 'NO_SHOW'));
