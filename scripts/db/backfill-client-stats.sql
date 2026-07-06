-- ============================================================
-- Backfill client analytics columns from appointments
-- (total_visits, cancelled_visits, ltv)
--
-- Why: these are denormalized counters on `clients`, not a separate
-- analytics table. Seed data and legacy imports may have appointments
-- without matching client stats.
--
-- Run: scripts/db/run-backfill-client-stats.sh
-- Safe to re-run (idempotent recalculation).
-- ============================================================

-- Normalize legacy appointment status values (DB constraint: UPPERCASE enum names)
UPDATE appointments
SET status = 'COMPLETED', updated_at = NOW()
WHERE deleted_at IS NULL
  AND UPPER(status) IN ('DONE', 'COMPLETED');

UPDATE appointments
SET status = 'SCHEDULED', updated_at = NOW()
WHERE deleted_at IS NULL
  AND UPPER(status) IN ('NEW', 'CONFIRMED', 'SCHEDULED', 'IN_PROGRESS');

UPDATE appointments
SET status = 'CANCELLED', updated_at = NOW()
WHERE deleted_at IS NULL
  AND UPPER(status) = 'CANCELLED';

UPDATE clients c
SET
    total_visits = COALESCE(stats.completed_count, 0),
    cancelled_visits = COALESCE(stats.cancelled_count, 0),
    ltv = COALESCE(stats.completed_revenue, 0),
    last_visit = stats.last_completed_at,
    updated_at = NOW()
FROM (
    SELECT
        a.client_id,
        COUNT(*) FILTER (WHERE a.status = 'COMPLETED')::INTEGER AS completed_count,
        COUNT(*) FILTER (WHERE a.status = 'CANCELLED')::INTEGER AS cancelled_count,
        COALESCE(SUM(a.final_price) FILTER (WHERE a.status = 'COMPLETED' AND a.final_price > 0), 0) AS completed_revenue,
        MAX(a.start_time) FILTER (WHERE a.status = 'COMPLETED') AS last_completed_at
    FROM appointments a
    WHERE a.deleted_at IS NULL
      AND a.client_id IS NOT NULL
    GROUP BY a.client_id
) stats
WHERE c.id = stats.client_id
  AND c.deleted_at IS NULL;

-- Clients with no appointments stay at 0; optionally zero-out stale mismatches:
UPDATE clients c
SET
    total_visits = 0,
    cancelled_visits = 0,
    ltv = 0,
    updated_at = NOW()
WHERE c.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM appointments a
      WHERE a.client_id = c.id AND a.deleted_at IS NULL
  )
  AND (c.total_visits <> 0 OR c.cancelled_visits <> 0 OR c.ltv <> 0);

-- Sofia Ivanenko demo sanity check (seed tenant)
SELECT
    c.first_name,
    c.last_name,
    c.total_visits,
    c.cancelled_visits,
    c.ltv,
    (SELECT COUNT(*) FROM appointments a WHERE a.client_id = c.id AND a.deleted_at IS NULL) AS appointment_rows
FROM clients c
WHERE c.id = 'e0000000-0000-0000-0000-000000000005';
