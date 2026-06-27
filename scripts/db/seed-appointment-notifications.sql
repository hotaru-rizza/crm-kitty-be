-- Demo email notifications linked to recent appointments (idempotent via dedupe_key).
-- Run: scripts/db/run-seed-appointment-notifications.sh

WITH recent_appointments AS (
    SELECT
        a.id AS appointment_id,
        a.tenant_id,
        COALESCE(c.email, 'client@example.com') AS recipient_email,
        TRIM(COALESCE(c.first_name, '') || ' ' || COALESCE(c.last_name, '')) AS recipient_name
    FROM appointments a
    JOIN clients c ON c.id = a.client_id
    WHERE NOT EXISTS (
        SELECT 1
        FROM email_message em
        WHERE em.entity_id = a.id
    )
    ORDER BY a.start_time DESC NULLS LAST, a.created_at DESC
    LIMIT 5
),
notification_rows AS (
    SELECT
        ra.appointment_id,
        ra.tenant_id,
        ra.recipient_email,
        NULLIF(ra.recipient_name, '') AS recipient_name,
        v.trigger_type,
        v.subject,
        v.body,
        v.status,
        v.created_offset,
        v.sent_offset
    FROM recent_appointments ra
    CROSS JOIN (
        VALUES
            ('BOOKING_CONFIRMED', 'Підтвердження запису', 'Ваш запис підтверджено.', 'SENT', INTERVAL '3 days', INTERVAL '3 days'),
            ('BEFORE_BOOKING', 'Нагадування перед візитом', 'Нагадуємо про ваш візит.', 'SENT', INTERVAL '1 day', INTERVAL '1 day'),
            ('BOOKING_RESCHEDULED', 'Повідомлення про перенесення', 'Запис перенесено.', 'PENDING', INTERVAL '2 hours', NULL::interval)
    ) AS v(trigger_type, subject, body, status, created_offset, sent_offset)
)
INSERT INTO email_message (
    id,
    tenant_id,
    trigger_type,
    recipient_email,
    recipient_name,
    subject,
    body,
    status,
    attempts,
    entity_id,
    dedupe_key,
    created_at,
    sent_at
)
SELECT
    gen_random_uuid(),
    nr.tenant_id,
    nr.trigger_type,
    nr.recipient_email,
    nr.recipient_name,
    nr.subject,
    nr.body,
    nr.status,
    CASE WHEN nr.status = 'SENT' THEN 1 ELSE 0 END,
    nr.appointment_id,
    'seed-appointment-notif-' || nr.appointment_id::text || '-' || nr.trigger_type,
    NOW() - nr.created_offset,
    CASE WHEN nr.sent_offset IS NULL THEN NULL ELSE NOW() - nr.sent_offset END
FROM notification_rows nr
WHERE NOT EXISTS (
    SELECT 1
    FROM email_message em
    WHERE em.dedupe_key = 'seed-appointment-notif-' || nr.appointment_id::text || '-' || nr.trigger_type
);
