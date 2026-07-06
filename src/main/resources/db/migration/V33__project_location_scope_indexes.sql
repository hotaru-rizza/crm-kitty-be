CREATE INDEX IF NOT EXISTS idx_appointment_project
    ON appointments (project_id)
    WHERE project_id IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_projects_location
    ON projects (location_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_staff_locations_location
    ON staff_locations (location_id);
