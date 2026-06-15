CREATE TABLE notification_preference (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    template_key    VARCHAR(64) NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    schedule_hours  INTEGER,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_notification_preference UNIQUE (tenant_id, template_key)
);

CREATE INDEX idx_notification_preference_tenant ON notification_preference (tenant_id);
CREATE INDEX idx_notification_preference_template_enabled ON notification_preference (template_key, enabled);

INSERT INTO notification_preference (tenant_id, template_key, enabled, schedule_hours)
SELECT t.id, 'BOOKING_CONFIRMED', TRUE, NULL
FROM tenants t;

INSERT INTO notification_preference (tenant_id, template_key, enabled, schedule_hours)
SELECT cs.tenant_id, 'BOOKING_REMINDER', TRUE, cs.reminder_hours_before
FROM company_settings cs;

INSERT INTO notification_preference (tenant_id, template_key, enabled, schedule_hours)
SELECT t.id, 'BOOKING_REMINDER', TRUE, 24
FROM tenants t
WHERE NOT EXISTS (
    SELECT 1 FROM notification_preference np
    WHERE np.tenant_id = t.id AND np.template_key = 'BOOKING_REMINDER'
);

INSERT INTO notification_preference (tenant_id, template_key, enabled, schedule_hours)
SELECT t.id, 'AFTERCARE_INSTRUCTIONS', FALSE, 24
FROM tenants t;

INSERT INTO notification_preference (tenant_id, template_key, enabled, schedule_hours)
SELECT t.id, key, FALSE, NULL
FROM tenants t
CROSS JOIN (VALUES
    ('BOOKING_CANCELED'),
    ('BOOKING_RESCHEDULED'),
    ('NEW_APPOINTMENT'),
    ('APPOINTMENT_CANCELED'),
    ('APPOINTMENT_CHANGED')
) AS defaults(key);

DROP TABLE IF EXISTS company_settings;

UPDATE tenants SET language = 'uk' WHERE language = 'ua';

ALTER TABLE tenants DROP COLUMN IF EXISTS subdomain;
ALTER TABLE tenants RENAME COLUMN logo TO logo_url;
