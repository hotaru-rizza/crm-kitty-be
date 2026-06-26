-- ============================================================
-- Client management test data (V20+: dormant, blacklisted, email matching)
-- Idempotent — safe to re-run on existing InkFlow seed tenant.
-- Run: scripts/db/run-seed-client-management-test-data.sh
-- ============================================================

-- Ensure V20 schema (safe if Flyway migration already applied)
ALTER TABLE clients
    ADD COLUMN IF NOT EXISTS dormant BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS blacklisted BOOLEAN NOT NULL DEFAULT false;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'clients' AND column_name = 'status'
    ) THEN
        UPDATE clients SET blacklisted = true WHERE status = 'BLACKLISTED';
        UPDATE clients SET dormant = true WHERE status = 'INACTIVE';
        ALTER TABLE clients DROP CONSTRAINT IF EXISTS clients_status_check;
        ALTER TABLE clients DROP COLUMN status;
    END IF;
END $$;

ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS client_dormancy_days INTEGER NOT NULL DEFAULT 90;

ALTER TABLE requests
    ADD COLUMN IF NOT EXISTS email VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_client_email_tenant
    ON clients (tenant_id, lower(email))
    WHERE deleted_at IS NULL AND email IS NOT NULL AND email <> '';

-- Tenant: dormancy period for settings UI + job
UPDATE tenants
SET client_dormancy_days = 90,
    updated_at = NOW()
WHERE id = 'a0000000-0000-0000-0000-000000000001';

-- Seed clients: explicit dormant / active flags (replaces old status enum)
UPDATE clients SET dormant = false, blacklisted = false, updated_at = NOW()
WHERE tenant_id = 'a0000000-0000-0000-0000-000000000001'
  AND id IN (
    'e0000000-0000-0000-0000-000000000001',
    'e0000000-0000-0000-0000-000000000002',
    'e0000000-0000-0000-0000-000000000003',
    'e0000000-0000-0000-0000-000000000004',
    'e0000000-0000-0000-0000-000000000005',
    'e0000000-0000-0000-0000-000000000006',
    'e0000000-0000-0000-0000-000000000007',
    'e0000000-0000-0000-0000-000000000008',
    'e0000000-0000-0000-0000-000000000009',
    'e0000000-0000-0000-0000-000000000011',
    'e0000000-0000-0000-0000-000000000012'
  );

-- Was INACTIVE → dormant
UPDATE clients SET dormant = true, blacklisted = false, updated_at = NOW()
WHERE id = 'e0000000-0000-0000-0000-000000000010';

-- Emails for picker search (clients that had none)
UPDATE clients SET email = 'maks.tkachenko@gmail.com', updated_at = NOW()
WHERE id = 'e0000000-0000-0000-0000-000000000004' AND email IS NULL;

UPDATE clients SET email = 'vlad.romanenko@gmail.com', updated_at = NOW()
WHERE id = 'e0000000-0000-0000-0000-000000000008' AND email IS NULL;

UPDATE clients SET email = 'taras.sirko@gmail.com', updated_at = NOW()
WHERE id = 'e0000000-0000-0000-0000-000000000012' AND email IS NULL;

-- Blacklisted client (appointment warning + convert block)
INSERT INTO clients (
    id, tenant_id, first_name, last_name, phone, email, instagram, source,
    dormant, blacklisted, total_visits, cancelled_visits, ltv, notes, created_at, updated_at
)
VALUES (
    'e0000000-0000-0000-0000-000000000013',
    'a0000000-0000-0000-0000-000000000001',
    'Віктор', 'Блокований', '+380501111013', 'viktor.blocked@gmail.com', NULL,
    'OTHER', false, true, 1, 2, 0.00,
    'У чорному списку — не приходив на записи, грубість.', NOW() - INTERVAL '200 days', NOW()
)
ON CONFLICT (id) DO UPDATE SET
    blacklisted = EXCLUDED.blacklisted,
    dormant = EXCLUDED.dormant,
    email = EXCLUDED.email,
    notes = EXCLUDED.notes,
    updated_at = NOW();

-- Dormant client (no visits > 90 days) — for Active/Dormant toggle
INSERT INTO clients (
    id, tenant_id, first_name, last_name, phone, email, instagram, source,
    dormant, blacklisted, total_visits, cancelled_visits, ltv, notes, created_at, updated_at
)
VALUES (
    'e0000000-0000-0000-0000-000000000014',
    'a0000000-0000-0000-0000-000000000001',
    'Павло', 'Старий', '+380501111014', 'pavlo.staryi@gmail.com', NULL,
    'REFERRAL', true, false, 2, 0, 5000.00,
    'Давно не був — тест dormant.', NOW() - INTERVAL '400 days', NOW()
)
ON CONFLICT (id) DO UPDATE SET
    dormant = EXCLUDED.dormant,
    blacklisted = EXCLUDED.blacklisted,
    email = EXCLUDED.email,
    updated_at = NOW();

-- Old appointment for dormant client (120 days ago)
INSERT INTO appointments (
    id, tenant_id, client_id, artist_id, service_id, location_id, project_id,
    start_time, end_time, status, price, prepayment, discount, final_price,
    notes, created_at, updated_at
)
VALUES (
    'aa000000-0000-0000-0000-000000000013',
    'a0000000-0000-0000-0000-000000000001',
    'e0000000-0000-0000-0000-000000000014',
    'c0000000-0000-0000-0000-000000000001',
    'd0000000-0000-0000-0000-000000000005',
    'b0000000-0000-0000-0000-000000000001',
    NULL,
    NOW() - INTERVAL '120 days' + INTERVAL '12 hours',
    NOW() - INTERVAL '120 days' + INTERVAL '12 hours 30 minutes',
     'COMPLETED', 0.00, 0.00, 0.00, 0.00,
    'Консультація — давній візит.', NOW() - INTERVAL '120 days', NOW()
)
ON CONFLICT (id) DO NOTHING;

-- Recent appointments for /clients/recent picker (last 7 days)
INSERT INTO appointments (
    id, tenant_id, client_id, artist_id, service_id, location_id, project_id,
    start_time, end_time, status, price, prepayment, discount, final_price,
    notes, created_at, updated_at
)
VALUES
    ('aa000000-0000-0000-0000-000000000014',
     'a0000000-0000-0000-0000-000000000001',
     'e0000000-0000-0000-0000-000000000001',
     'c0000000-0000-0000-0000-000000000002',
     'd0000000-0000-0000-0000-000000000001',
     'b0000000-0000-0000-0000-000000000001', NULL,
     NOW() - INTERVAL '2 days' + INTERVAL '11 hours',
     NOW() - INTERVAL '2 days' + INTERVAL '13 hours',
     'COMPLETED', 2500.00, 500.00, 0.00, 2500.00,
     'Recent — для picker Recent.', NOW() - INTERVAL '2 days', NOW()),

    ('aa000000-0000-0000-0000-000000000015',
     'a0000000-0000-0000-0000-000000000001',
     'e0000000-0000-0000-0000-000000000003',
     'c0000000-0000-0000-0000-000000000001',
     'd0000000-0000-0000-0000-000000000002',
     'b0000000-0000-0000-0000-000000000001',
     'f0000000-0000-0000-0000-000000000001',
     NOW() - INTERVAL '1 day' + INTERVAL '10 hours',
     NOW() - INTERVAL '1 day' + INTERVAL '14 hours',
     'COMPLETED', 4500.00, 1000.00, 0.00, 4500.00,
     'Recent — рукав сеанс.', NOW() - INTERVAL '1 day', NOW()),

    ('aa000000-0000-0000-0000-000000000016',
     'a0000000-0000-0000-0000-000000000001',
     'e0000000-0000-0000-0000-000000000011',
     'c0000000-0000-0000-0000-000000000003',
     'd0000000-0000-0000-0000-000000000001',
     'b0000000-0000-0000-0000-000000000001', NULL,
     NOW() - INTERVAL '3 days' + INTERVAL '15 hours',
     NOW() - INTERVAL '3 days' + INTERVAL '17 hours',
     'COMPLETED', 2500.00, 0.00, 0.00, 2500.00,
     'Recent — портрет кота.', NOW() - INTERVAL '3 days', NOW())
ON CONFLICT (id) DO NOTHING;

-- Requests: email matching scenarios
UPDATE requests SET email = 'igor.zakharenko@gmail.com'
WHERE id = 'ab000000-0000-0000-0000-000000000004';

-- New requests for convert / book / blacklist flows
INSERT INTO requests (
    id, tenant_id, source, client_name, client_nickname, message, phone, email, instagram,
    status, location_id, staff_id,
    tattoo_timing, tattoo_size, body_zones, is_cover_up, idea, reference_urls, sketch_url,
    city, contact_method, contact_value,
    replied_at, converted_at, converted_client_id,
    created_at
)
VALUES
    -- matchedClientId → Book (email matches Олексій Петренко e002)
    ('ab000000-0000-0000-0000-000000000009',
     'a0000000-0000-0000-0000-000000000001',
     'WEBSITE', 'Олексій Петренко', NULL,
     'Повторна заявка — хочу другий сеанс портрета. Мій email той самий.',
     '+380501111002', 'oleksii.p@gmail.com', NULL, 'NEW',
     'b0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000003',
     'weeks', 'palm', 'front:front-deltoids-left', false,
     'Продовження портрета реалізм', NULL, NULL,
     'kyiv', 'email', 'oleksii.p@gmail.com',
     NULL, NULL, NULL,
     NOW() - INTERVAL '4 hours'),

    -- matchedClientId + blacklisted → warning in details
    ('ab000000-0000-0000-0000-000000000010',
     'a0000000-0000-0000-0000-000000000001',
     'INSTAGRAM', 'Віктор Б.', NULL,
     'Хочу записатися знову, обіцяю бути вчасно.',
     '+380501111013', 'viktor.blocked@gmail.com', NULL, 'NEW',
     'b0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001',
     'asap', 'credit-card', 'front:forearm-left', false,
     'Невелике тату — тест blacklist match', NULL, NULL,
     'kyiv', 'email', 'viktor.blocked@gmail.com',
     NULL, NULL, NULL,
     NOW() - INTERVAL '6 hours'),

    -- No match → Convert / Convert+Book (new email)
    ('ab000000-0000-0000-0000-000000000011',
     'a0000000-0000-0000-0000-000000000001',
     'TELEGRAM', 'Нова Клієнтка', '@new.client',
     'Перше тату — маленький символ на щиколотці.',
     '+380501119999', 'new.client.test@gmail.com', NULL, 'NEW',
     'b0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000002',
     'weeks', 'credit-card', 'front:ankle-left', false,
     'Мінімалізм на щиколотці', NULL, NULL,
     'kyiv', 'email', 'new.client.test@gmail.com',
     NULL, NULL, NULL,
     NOW() - INTERVAL '30 minutes'),

    -- matchedClientId → Book (Ірина Савченко e001)
    ('ab000000-0000-0000-0000-000000000012',
     'a0000000-0000-0000-0000-000000000001',
     'REFERRAL', 'Ірина С.', NULL,
     'Хочу touch-up попередньої роботи.',
     '+380501111001', 'iryna.savchenko@gmail.com', '@iryna.s', 'NEW',
     'b0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000002',
     'flexible', 'credit-card', 'front:forearm-left', false,
     'Touch-up фінлайн', NULL, NULL,
     'kyiv', 'email', 'iryna.savchenko@gmail.com',
     NULL, NULL, NULL,
     NOW() - INTERVAL '1 hour')
ON CONFLICT (id) DO UPDATE SET
    email = EXCLUDED.email,
    message = EXCLUDED.message,
    status = EXCLUDED.status,
    staff_id = EXCLUDED.staff_id,
    tattoo_timing = EXCLUDED.tattoo_timing,
    tattoo_size = EXCLUDED.tattoo_size,
    city = EXCLUDED.city,
    contact_method = EXCLUDED.contact_method,
    contact_value = EXCLUDED.contact_value;

-- Summary for manual verification
SELECT 'clients_active' AS metric, COUNT(*)::text AS value
FROM clients
WHERE tenant_id = 'a0000000-0000-0000-0000-000000000001'
  AND deleted_at IS NULL AND dormant = false AND blacklisted = false
UNION ALL
SELECT 'clients_dormant', COUNT(*)::text
FROM clients
WHERE tenant_id = 'a0000000-0000-0000-0000-000000000001'
  AND deleted_at IS NULL AND dormant = true
UNION ALL
SELECT 'clients_blacklisted', COUNT(*)::text
FROM clients
WHERE tenant_id = 'a0000000-0000-0000-0000-000000000001'
  AND deleted_at IS NULL AND blacklisted = true
UNION ALL
SELECT 'requests_with_email', COUNT(*)::text
FROM requests
WHERE tenant_id = 'a0000000-0000-0000-0000-000000000001'
  AND email IS NOT NULL AND email <> '';
