ALTER TABLE locations
    ADD COLUMN working_hours_start TIME NOT NULL DEFAULT '09:00:00',
    ADD COLUMN working_hours_end TIME NOT NULL DEFAULT '22:00:00';
