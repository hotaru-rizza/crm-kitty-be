CREATE TABLE appointment_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    appointment_id  UUID NOT NULL REFERENCES appointments(id) ON DELETE CASCADE,
    service_id      UUID REFERENCES services(id),
    source          VARCHAR(32) NOT NULL,
    title           VARCHAR(255) NOT NULL,
    quantity        INTEGER NOT NULL DEFAULT 1,
    unit_price      NUMERIC(10, 2) NOT NULL,
    duration_minutes INTEGER NOT NULL DEFAULT 0,
    line_total      NUMERIC(10, 2) NOT NULL,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ,
    created_by      UUID,
    updated_by      UUID,
    deleted_at      TIMESTAMPTZ
);

CREATE INDEX idx_appointment_item_appointment ON appointment_items (appointment_id);
CREATE INDEX idx_appointment_item_tenant ON appointment_items (tenant_id);

INSERT INTO appointment_items (
    tenant_id,
    appointment_id,
    service_id,
    source,
    title,
    quantity,
    unit_price,
    duration_minutes,
    line_total,
    sort_order,
    created_at,
    updated_at
)
SELECT
    a.tenant_id,
    a.id,
    a.service_id,
    'SERVICE',
    s.title,
    1,
    a.price,
    GREATEST(
        EXTRACT(EPOCH FROM (a.end_time - a.start_time))::INTEGER / 60,
        COALESCE(s.duration, 0)
    ),
    a.price,
    0,
    a.created_at,
    a.updated_at
FROM appointments a
JOIN services s ON s.id = a.service_id
WHERE a.deleted_at IS NULL;
