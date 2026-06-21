ALTER TABLE projects
    DROP CONSTRAINT IF EXISTS projects_status_check;

UPDATE projects
SET status = 'ARCHIVED'
WHERE status IN ('ON_HOLD', 'CANCELLED');

ALTER TABLE projects
    ADD CONSTRAINT projects_status_check
        CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'ARCHIVED'));
