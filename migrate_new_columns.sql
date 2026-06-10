-- Staff: new columns from last commit
ALTER TABLE staff ADD COLUMN IF NOT EXISTS is_public      BOOLEAN     NOT NULL DEFAULT false;
ALTER TABLE staff ADD COLUMN IF NOT EXISTS hourly_rate    NUMERIC(10,2);
ALTER TABLE staff ADD COLUMN IF NOT EXISTS studio_photo_url TEXT;

-- Staff: new collection table for "don't do" list
CREATE TABLE IF NOT EXISTS staff_dont_do (
    staff_id UUID NOT NULL REFERENCES staff(id) ON DELETE CASCADE,
    item     TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_staff_dont_do_staff_id ON staff_dont_do(staff_id);

-- Location: new columns
ALTER TABLE locations ADD COLUMN IF NOT EXISTS latitude  DOUBLE PRECISION;
ALTER TABLE locations ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION;
ALTER TABLE locations ADD COLUMN IF NOT EXISTS city      VARCHAR(255);

-- Request: new columns
ALTER TABLE requests ADD COLUMN IF NOT EXISTS staff_id        UUID REFERENCES staff(id);
ALTER TABLE requests ADD COLUMN IF NOT EXISTS consumer_user_id UUID;
ALTER TABLE requests ADD COLUMN IF NOT EXISTS tattoo_timing   VARCHAR(30);
ALTER TABLE requests ADD COLUMN IF NOT EXISTS tattoo_size     VARCHAR(30);
ALTER TABLE requests ADD COLUMN IF NOT EXISTS body_zones      TEXT;
ALTER TABLE requests ADD COLUMN IF NOT EXISTS is_cover_up     BOOLEAN;
ALTER TABLE requests ADD COLUMN IF NOT EXISTS idea            TEXT;
ALTER TABLE requests ADD COLUMN IF NOT EXISTS reference_urls  TEXT;
ALTER TABLE requests ADD COLUMN IF NOT EXISTS city            VARCHAR(50);
ALTER TABLE requests ADD COLUMN IF NOT EXISTS contact_method  VARCHAR(20);
ALTER TABLE requests ADD COLUMN IF NOT EXISTS contact_value   VARCHAR(255);

-- New tables for consumer module
CREATE TABLE IF NOT EXISTS consumer_users (
    id         UUID        PRIMARY KEY,
    email      VARCHAR(255),
    avatar_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS ai_generations (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    consumer_id  UUID        REFERENCES consumer_users(id),
    input_url    TEXT,
    output_url   TEXT,
    prompt       TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- New tables for notifications module
CREATE TABLE IF NOT EXISTS notifications (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID        NOT NULL,
    recipient_id UUID        NOT NULL,
    channel      VARCHAR(50) NOT NULL,
    type         VARCHAR(50) NOT NULL,
    title        VARCHAR(255) NOT NULL,
    body         TEXT,
    data         TEXT,
    is_read      BOOLEAN     NOT NULL DEFAULT false,
    is_sent      BOOLEAN     NOT NULL DEFAULT false,
    sent_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_notif_recipient ON notifications(recipient_id, is_read, created_at);
CREATE INDEX IF NOT EXISTS idx_notif_tenant    ON notifications(tenant_id, created_at);

CREATE TABLE IF NOT EXISTS device_tokens (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL,
    token      TEXT        NOT NULL UNIQUE,
    platform   VARCHAR(20),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_device_tokens_user_id ON device_tokens(user_id);

-- Tattoos catalog
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS tattoos (
    id              BIGSERIAL   PRIMARY KEY,
    source          VARCHAR(50) NOT NULL DEFAULT 'unsplash',
    source_id       VARCHAR(255) NOT NULL,
    image_url       TEXT        NOT NULL,
    thumbnail_url   TEXT        NOT NULL,
    width           INTEGER     NOT NULL,
    height          INTEGER     NOT NULL,
    blur_hash       VARCHAR(100),
    dominant_color  VARCHAR(7),
    author_name     VARCHAR(255) NOT NULL,
    author_url      TEXT,
    description     TEXT,
    alt_description TEXT,
    tags            TEXT[],
    embedding       vector(1024),
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    UNIQUE(source, source_id)
);
CREATE INDEX IF NOT EXISTS idx_tattoos_source_id ON tattoos(source, source_id);

-- Staff: is_service_provider flag
ALTER TABLE staff ADD COLUMN IF NOT EXISTS is_service_provider BOOLEAN NOT NULL DEFAULT true;

-- Staff invites: is_service_provider flag + make calendar_color nullable
ALTER TABLE staff_invites ADD COLUMN IF NOT EXISTS is_service_provider BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE staff_invites ALTER COLUMN calendar_color DROP NOT NULL;

-- Set existing admins as non-service-providers
UPDATE staff SET is_service_provider = false WHERE role = 'ADMIN';
