ALTER TABLE locations
    ADD COLUMN is_default BOOLEAN NOT NULL DEFAULT false;

-- One default per tenant: earliest active location
UPDATE locations l
SET is_default = true
FROM (
    SELECT DISTINCT ON (tenant_id) id
    FROM locations
    WHERE deleted_at IS NULL
    ORDER BY tenant_id, created_at ASC
) first_location
WHERE l.id = first_location.id;

CREATE UNIQUE INDEX uq_locations_one_default_per_tenant
    ON locations (tenant_id)
    WHERE is_default = true AND deleted_at IS NULL;
