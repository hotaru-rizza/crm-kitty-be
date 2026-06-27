-- Backfill missing appointment audit history (idempotent via dedupe_key in details field pattern).
-- Run: scripts/db/run-backfill-appointment-audit-history.sh

WITH appointments_without_history AS (
    SELECT
        a.id,
        a.tenant_id,
        a.start_time,
        c.first_name,
        c.last_name,
        c.id AS client_id
    FROM appointments a
    JOIN clients c ON c.id = a.client_id
    WHERE NOT EXISTS (
        SELECT 1
        FROM audit_log al
        WHERE al.entity_type = 'APPOINTMENT'
          AND al.entity_id = a.id::text
    )
    ORDER BY a.updated_at DESC NULLS LAST, a.created_at DESC
    LIMIT 20
)
INSERT INTO audit_log (
    id,
    tenant_id,
    actor_name,
    action,
    entity_type,
    entity_id,
    entity_label,
    subject_client_id,
    details,
    created_at
)
SELECT
    gen_random_uuid(),
    awh.tenant_id,
    'system',
    'UPDATE',
    'APPOINTMENT',
    awh.id::text,
    TRIM(awh.first_name || ' ' || awh.last_name) || ' @ ' || awh.start_time::text,
    awh.client_id,
    'backfill-appointment-audit-' || awh.id::text,
    NOW() - INTERVAL '1 minute'
FROM appointments_without_history awh
WHERE NOT EXISTS (
    SELECT 1
    FROM audit_log al
    WHERE al.details = 'backfill-appointment-audit-' || awh.id::text
);
