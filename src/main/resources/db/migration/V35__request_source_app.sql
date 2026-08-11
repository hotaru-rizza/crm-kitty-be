-- Allow mobile app as a request/client source (RequestSource.APP)
ALTER TABLE requests
    DROP CONSTRAINT IF EXISTS requests_source_check;

ALTER TABLE requests
    ADD CONSTRAINT requests_source_check CHECK (
        source::text = ANY (ARRAY[
            'INSTAGRAM',
            'TELEGRAM',
            'WEBSITE',
            'REFERRAL',
            'WALK_IN',
            'OTHER',
            'APP'
        ]::text[])
    );

ALTER TABLE clients
    DROP CONSTRAINT IF EXISTS clients_source_check;

ALTER TABLE clients
    ADD CONSTRAINT clients_source_check CHECK (
        source IS NULL OR source::text = ANY (ARRAY[
            'INSTAGRAM',
            'TELEGRAM',
            'WEBSITE',
            'REFERRAL',
            'WALK_IN',
            'OTHER',
            'APP'
        ]::text[])
    );
