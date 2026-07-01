-- Legacy repair: subdomain was removed in V5 but some databases still have the column.
ALTER TABLE tenants DROP CONSTRAINT IF EXISTS ukowdadmekvhjx3lk2yt24s9pyw;
ALTER TABLE tenants DROP COLUMN IF EXISTS subdomain;
