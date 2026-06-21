-- Dev/demo backfill: requests created before consumer booking fields existed.
-- Safe to re-run: COALESCE keeps existing values.

UPDATE requests SET
    tattoo_timing = COALESCE(
        tattoo_timing,
        (ARRAY['asap', 'weeks', 'months', 'flexible', 'browsing'])[1 + (abs(hashtext(id::text)) % 5)]
    ),
    tattoo_size = COALESCE(
        tattoo_size,
        (ARRAY['credit-card', 'palm', 'hand', 'half-sleeve', 'undecided'])[1 + (abs(hashtext(id::text)) % 5)]
    ),
    city = COALESCE(
        city,
        (ARRAY['kyiv', 'lviv', 'odesa', 'kharkiv', 'dnipro', 'zaporizhzhia', 'vinnytsia'])[1 + (abs(hashtext(client_name)) % 7)]
    )
WHERE tattoo_timing IS NULL
   OR tattoo_size IS NULL
   OR city IS NULL;
