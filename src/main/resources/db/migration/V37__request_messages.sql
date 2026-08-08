CREATE TABLE request_messages (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL,
    request_id       UUID NOT NULL REFERENCES requests (id) ON DELETE CASCADE,
    sender_type      VARCHAR(16) NOT NULL,
    sender_staff_id  UUID REFERENCES staff (id),
    sender_name      VARCHAR(255),
    body             TEXT NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT request_messages_sender_type_check CHECK (
        sender_type IN ('CLIENT', 'STAFF', 'SYSTEM')
    )
);

CREATE INDEX idx_request_message_request_created
    ON request_messages (request_id, created_at);

CREATE INDEX idx_request_message_tenant
    ON request_messages (tenant_id);

INSERT INTO request_messages (tenant_id, request_id, sender_type, body, created_at)
SELECT r.tenant_id, r.id, 'SYSTEM', 'Заявка створена', r.created_at
FROM requests r
WHERE NOT EXISTS (
    SELECT 1 FROM request_messages rm WHERE rm.request_id = r.id
);

INSERT INTO request_messages (tenant_id, request_id, sender_type, sender_name, body, created_at)
SELECT
    r.tenant_id,
    r.id,
    'CLIENT',
    r.client_name,
    r.message,
    r.created_at + INTERVAL '1 millisecond'
FROM requests r
WHERE r.message IS NOT NULL
  AND btrim(r.message) <> ''
  AND NOT EXISTS (
      SELECT 1
      FROM request_messages rm
      WHERE rm.request_id = r.id
        AND rm.sender_type = 'CLIENT'
  );
