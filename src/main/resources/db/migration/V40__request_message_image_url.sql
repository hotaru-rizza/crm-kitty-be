ALTER TABLE request_messages
    ALTER COLUMN body DROP NOT NULL;

ALTER TABLE request_messages
    ADD COLUMN image_url TEXT;

ALTER TABLE request_messages
    ADD CONSTRAINT request_messages_body_or_image_check
        CHECK (
            (body IS NOT NULL AND btrim(body) <> '')
            OR (image_url IS NOT NULL AND btrim(image_url) <> '')
        );
