-- Align email_message column sizes with JPA entity (body/subject can exceed 255 chars)
ALTER TABLE email_message
    ALTER COLUMN subject TYPE TEXT;

ALTER TABLE email_message
    ALTER COLUMN body TYPE TEXT;

ALTER TABLE email_message
    ALTER COLUMN last_error TYPE VARCHAR(512);
