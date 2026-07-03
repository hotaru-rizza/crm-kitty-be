CREATE UNIQUE INDEX IF NOT EXISTS uq_staff_auth_user_id_active
    ON staff (auth_user_id)
    WHERE deleted_at IS NULL AND auth_user_id IS NOT NULL;
