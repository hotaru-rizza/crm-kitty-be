-- Dashboard preview: shift dates on existing rows (safe to re-run).
-- Usage: ./scripts/db/run-dashboard-preview-dates.sh

-- Appointments today (Kyiv), up to 4 rows — excluded from month spread below
WITH today_picks AS (
  SELECT id, ROW_NUMBER() OVER (ORDER BY start_time DESC NULLS LAST) AS rn
  FROM appointments
  WHERE lower(status::text) NOT IN ('cancelled', 'no_show')
  LIMIT 4
)
UPDATE appointments AS a
SET
  start_time = (date_trunc('day', timezone('Europe/Kyiv', now())) AT TIME ZONE 'Europe/Kyiv')
    + CASE today_picks.rn
      WHEN 1 THEN INTERVAL '10 hours'
      WHEN 2 THEN INTERVAL '12 hours 30 minutes'
      WHEN 3 THEN INTERVAL '14 hours'
      ELSE INTERVAL '16 hours 30 minutes'
    END,
  end_time = (date_trunc('day', timezone('Europe/Kyiv', now())) AT TIME ZONE 'Europe/Kyiv')
    + CASE today_picks.rn
      WHEN 1 THEN INTERVAL '12 hours'
      WHEN 2 THEN INTERVAL '14 hours 30 minutes'
      WHEN 3 THEN INTERVAL '17 hours'
      ELSE INTERVAL '18 hours 30 minutes'
    END,
  status = CASE
    WHEN lower(a.status::text) IN ('completed', 'done') THEN a.status
    ELSE 'confirmed'
  END,
  updated_at = NOW()
FROM today_picks
WHERE a.id = today_picks.id;

-- Completed appointments in current month (top services) — skip today's picks
WITH today_picks AS (
  SELECT id
  FROM appointments
  WHERE start_time >= (date_trunc('day', timezone('Europe/Kyiv', now())) AT TIME ZONE 'Europe/Kyiv')
    AND start_time < (date_trunc('day', timezone('Europe/Kyiv', now())) AT TIME ZONE 'Europe/Kyiv') + INTERVAL '1 day'
),
month_picks AS (
  SELECT id, ROW_NUMBER() OVER (ORDER BY start_time DESC NULLS LAST) AS rn
  FROM appointments
  WHERE lower(status::text) IN ('completed', 'done')
    AND id NOT IN (SELECT id FROM today_picks)
  LIMIT 8
)
UPDATE appointments AS a
SET
  start_time = (date_trunc('month', timezone('Europe/Kyiv', now())) AT TIME ZONE 'Europe/Kyiv')
    + ((month_picks.rn % 20) + 1) * INTERVAL '1 day'
    + INTERVAL '11 hours',
  end_time = (date_trunc('month', timezone('Europe/Kyiv', now())) AT TIME ZONE 'Europe/Kyiv')
    + ((month_picks.rn % 20) + 1) * INTERVAL '1 day'
    + INTERVAL '14 hours',
  updated_at = NOW()
FROM month_picks
WHERE a.id = month_picks.id;

-- Upcoming birthdays (next 7 days)
WITH ranked AS (
  SELECT id, ROW_NUMBER() OVER (ORDER BY created_at) AS rn
  FROM clients
  LIMIT 5
)
UPDATE clients AS c
SET
  birth_date = (timezone('Europe/Kyiv', now()))::date + CASE ranked.rn
    WHEN 1 THEN 0
    WHEN 2 THEN 1
    WHEN 3 THEN 3
    WHEN 4 THEN 5
    ELSE 6
  END,
  updated_at = NOW()
FROM ranked
WHERE c.id = ranked.id;
