-- =============================================================
-- Supabase Custom Access Token Hook
-- Добавляет tenant_id, role, location_ids в JWT при каждом входе
--
-- Инструкция по подключению:
-- 1. Запустить этот скрипт в Supabase → SQL Editor
-- 2. Зайти в Authentication → Hooks
-- 3. Включить "Custom Access Token" hook
-- 4. Выбрать функцию: public.custom_access_token_hook
-- =============================================================

CREATE OR REPLACE FUNCTION public.custom_access_token_hook(event JSONB)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    claims         JSONB;
    staff_record   RECORD;
    user_id_text   TEXT;
BEGIN
    claims       := event -> 'claims';
    user_id_text := event ->> 'user_id';

    -- Ищем сотрудника по auth_user_id (Supabase UUID)
    SELECT
        s.tenant_id,
        s.role,
        COALESCE(
            ARRAY(
                SELECT sl.location_id::TEXT
                FROM staff_locations sl
                WHERE sl.staff_id = s.id
            ),
            ARRAY[]::TEXT[]
        ) AS location_ids
    INTO staff_record
    FROM staff s
    WHERE s.auth_user_id = user_id_text
      AND s.deleted_at IS NULL
    LIMIT 1;

    IF FOUND THEN
        claims := jsonb_set(claims, '{tenant_id}',    to_jsonb(staff_record.tenant_id::TEXT));
        claims := jsonb_set(claims, '{role}',         to_jsonb(lower(staff_record.role::TEXT)));
        claims := jsonb_set(claims, '{location_ids}', to_jsonb(staff_record.location_ids));
    END IF;

    RETURN jsonb_set(event, '{claims}', claims);
END;
$$;

-- Предоставляем необходимые права
GRANT USAGE ON SCHEMA public TO supabase_auth_admin;
GRANT EXECUTE ON FUNCTION public.custom_access_token_hook TO supabase_auth_admin;
GRANT SELECT ON TABLE public.staff TO supabase_auth_admin;
GRANT SELECT ON TABLE public.staff_locations TO supabase_auth_admin;
REVOKE EXECUTE ON FUNCTION public.custom_access_token_hook FROM authenticated, anon, public;
