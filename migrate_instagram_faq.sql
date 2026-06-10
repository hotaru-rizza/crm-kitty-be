-- Location: add instagram field
ALTER TABLE locations ADD COLUMN IF NOT EXISTS instagram VARCHAR(255);

-- Staff FAQ table
CREATE TABLE IF NOT EXISTS staff_faq (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    staff_id   UUID        NOT NULL REFERENCES staff(id) ON DELETE CASCADE,
    question   TEXT        NOT NULL,
    answer     TEXT        NOT NULL,
    sort_order INTEGER     NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_staff_faq_staff_id ON staff_faq(staff_id, sort_order);
