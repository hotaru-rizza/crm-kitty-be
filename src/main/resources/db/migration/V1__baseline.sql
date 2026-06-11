--
-- PostgreSQL database dump
--


-- Dumped from database version 17.6
-- Dumped by pg_dump version 18.4

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA public;


--
-- Name: SCHEMA public; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON SCHEMA public IS 'standard public schema';


--
-- Name: custom_access_token_hook(jsonb); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.custom_access_token_hook(event jsonb) RETURNS jsonb
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
    claims         JSONB;
    staff_record   RECORD;
    user_id_text   TEXT;
BEGIN
    claims       := event -> 'claims';
    user_id_text := event ->> 'user_id';

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
        claims := jsonb_set(claims, '{user_role}',    to_jsonb(lower(staff_record.role::TEXT)));
        claims := jsonb_set(claims, '{location_ids}', to_jsonb(staff_record.location_ids));
    END IF;

    RETURN jsonb_set(event, '{claims}', claims);

EXCEPTION WHEN OTHERS THEN
    RETURN event;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: ai_generations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ai_generations (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid,
    prompt character varying(1000),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    image_url character varying(255) NOT NULL
);


--
-- Name: appointments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.appointments (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    created_by uuid,
    deleted_at timestamp(6) with time zone,
    tenant_id uuid NOT NULL,
    updated_at timestamp(6) with time zone,
    updated_by uuid,
    cancellation_reason character varying(255),
    cancelled_at timestamp(6) with time zone,
    discount numeric(10,2) NOT NULL,
    end_time timestamp(6) with time zone NOT NULL,
    final_price numeric(10,2) NOT NULL,
    notes text,
    prepayment numeric(10,2) NOT NULL,
    price numeric(10,2) NOT NULL,
    sketch_image character varying(255),
    start_time timestamp(6) with time zone NOT NULL,
    status character varying(255) NOT NULL,
    artist_id uuid NOT NULL,
    client_id uuid NOT NULL,
    location_id uuid NOT NULL,
    project_id uuid,
    service_id uuid NOT NULL,
    consent_token character varying(255),
    google_event_id character varying(255),
    CONSTRAINT appointments_status_check CHECK (((status)::text = ANY ((ARRAY['NEW'::character varying, 'CONFIRMED'::character varying, 'IN_PROGRESS'::character varying, 'DONE'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: artist_service_pricing; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.artist_service_pricing (
    id uuid NOT NULL,
    duration integer,
    price numeric(10,2) NOT NULL,
    service_id uuid NOT NULL,
    staff_id uuid NOT NULL
);


--
-- Name: audit_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_log (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    actor_id uuid,
    actor_name character varying(255),
    action character varying(255) NOT NULL,
    entity_type character varying(255),
    entity_id character varying(255),
    entity_label character varying(255),
    details text,
    ip_address character varying(255),
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: client_medical_conditions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.client_medical_conditions (
    client_id uuid NOT NULL,
    condition character varying(255)
);


--
-- Name: client_tags; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.client_tags (
    client_id uuid NOT NULL,
    tag character varying(255)
);


--
-- Name: clients; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.clients (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    created_by uuid,
    deleted_at timestamp(6) with time zone,
    tenant_id uuid NOT NULL,
    updated_at timestamp(6) with time zone,
    updated_by uuid,
    avatar character varying(255),
    birth_date date,
    cancelled_visits integer NOT NULL,
    email character varying(255),
    first_name character varying(255) NOT NULL,
    instagram character varying(255),
    last_name character varying(255) NOT NULL,
    last_visit timestamp(6) with time zone,
    ltv numeric(12,2) NOT NULL,
    notes text,
    phone character varying(255) NOT NULL,
    source character varying(255),
    status character varying(255) NOT NULL,
    telegram character varying(255),
    total_visits integer NOT NULL,
    location_id uuid,
    CONSTRAINT clients_source_check CHECK (((source)::text = ANY ((ARRAY['INSTAGRAM'::character varying, 'TELEGRAM'::character varying, 'WEBSITE'::character varying, 'REFERRAL'::character varying, 'WALK_IN'::character varying, 'OTHER'::character varying])::text[]))),
    CONSTRAINT clients_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying, 'BLACKLISTED'::character varying])::text[])))
);


--
-- Name: company_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.company_settings (
    tenant_id uuid NOT NULL,
    allow_online_booking boolean NOT NULL,
    email_reminders boolean NOT NULL,
    max_advance_days integer NOT NULL,
    min_advance_hours integer NOT NULL,
    reminder_hours_before integer NOT NULL,
    sms_reminders boolean NOT NULL,
    telegram_reminders boolean NOT NULL,
    updated_at timestamp(6) with time zone,
    updated_by uuid,
    working_hours_end time(6) without time zone NOT NULL,
    working_hours_start time(6) without time zone NOT NULL,
    email_confirmations boolean DEFAULT true NOT NULL,
    email_aftercare boolean DEFAULT false NOT NULL,
    email_templates jsonb,
    email_cancellation boolean DEFAULT false NOT NULL,
    email_reschedule boolean DEFAULT false NOT NULL,
    email_staff_new_appointment boolean DEFAULT false NOT NULL,
    email_staff_cancellation boolean DEFAULT false NOT NULL,
    email_staff_reschedule boolean DEFAULT false NOT NULL
);


--
-- Name: consumer_favorite_generations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.consumer_favorite_generations (
    user_id uuid NOT NULL,
    generation_id uuid NOT NULL
);


--
-- Name: consumer_saved_artists; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.consumer_saved_artists (
    user_id uuid NOT NULL,
    artist_id character varying(255) NOT NULL
);


--
-- Name: consumer_saved_tattoos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.consumer_saved_tattoos (
    user_id uuid NOT NULL,
    tattoo_id bigint NOT NULL
);


--
-- Name: consumer_users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.consumer_users (
    id uuid NOT NULL,
    email character varying(255),
    avatar_url character varying(255),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    name character varying(255),
    ai_tokens integer DEFAULT 5 NOT NULL
);


--
-- Name: device_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.device_tokens (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    token text NOT NULL,
    platform character varying(255),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    last_used_at timestamp(6) with time zone
);


--
-- Name: email_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.email_logs (
    id uuid NOT NULL,
    appointment_id uuid,
    error_message character varying(255),
    recipient_email character varying(255) NOT NULL,
    recipient_name character varying(255),
    sent_at timestamp(6) with time zone NOT NULL,
    status character varying(255) NOT NULL,
    subject character varying(255) NOT NULL,
    tenant_id uuid NOT NULL,
    type character varying(255) NOT NULL,
    CONSTRAINT email_logs_status_check CHECK (((status)::text = ANY ((ARRAY['SENT'::character varying, 'FAILED'::character varying])::text[]))),
    CONSTRAINT email_logs_type_check CHECK (((type)::text = ANY ((ARRAY['CONFIRMATION'::character varying, 'REMINDER'::character varying, 'AFTERCARE'::character varying, 'MANUAL'::character varying])::text[])))
);


--
-- Name: gallery_photos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.gallery_photos (
    id uuid NOT NULL,
    body_part character varying(255),
    description character varying(255),
    stage character varying(255) NOT NULL,
    tenant_id uuid NOT NULL,
    uploaded_at timestamp(6) with time zone NOT NULL,
    uploaded_by uuid,
    url character varying(255) NOT NULL,
    appointment_id uuid,
    project_id uuid,
    CONSTRAINT gallery_photos_stage_check CHECK (((stage)::text = ANY ((ARRAY['SKETCH'::character varying, 'IN_PROGRESS'::character varying, 'FRESH'::character varying, 'HEALED'::character varying])::text[])))
);


--
-- Name: leave_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.leave_requests (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    created_by uuid,
    deleted_at timestamp(6) with time zone,
    tenant_id uuid NOT NULL,
    updated_at timestamp(6) with time zone,
    updated_by uuid,
    approved_at timestamp(6) with time zone,
    end_date date NOT NULL,
    leave_type character varying(255) NOT NULL,
    notes character varying(255),
    reason character varying(255),
    start_date date NOT NULL,
    status character varying(255) NOT NULL,
    approved_by uuid,
    staff_id uuid NOT NULL,
    CONSTRAINT leave_requests_leave_type_check CHECK (((leave_type)::text = ANY ((ARRAY['VACATION'::character varying, 'SICK_LEAVE'::character varying, 'PERSONAL'::character varying, 'OTHER'::character varying])::text[]))),
    CONSTRAINT leave_requests_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[])))
);


--
-- Name: locations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.locations (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    created_by uuid,
    deleted_at timestamp(6) with time zone,
    tenant_id uuid NOT NULL,
    updated_at timestamp(6) with time zone,
    updated_by uuid,
    address character varying(255) NOT NULL,
    color character varying(255) NOT NULL,
    google_maps_link character varying(255),
    is_active boolean NOT NULL,
    name character varying(255) NOT NULL,
    phone character varying(255),
    photo_url text,
    latitude double precision,
    longitude double precision,
    city character varying(255),
    navigation_instructions text,
    telegram_contact character varying(255),
    instagram character varying(255)
);


--
-- Name: monobank_invoices; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.monobank_invoices (
    id uuid NOT NULL,
    amount numeric(12,2) NOT NULL,
    appointment_id uuid NOT NULL,
    ccy integer,
    created_at timestamp(6) with time zone NOT NULL,
    expires_at timestamp(6) with time zone,
    monobank_invoice_id character varying(255) NOT NULL,
    page_url character varying(512) NOT NULL,
    paid_at timestamp(6) with time zone,
    payment_type character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    tenant_id uuid NOT NULL,
    transaction_id uuid,
    invoice_type character varying(255) NOT NULL
);


--
-- Name: notifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notifications (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    recipient_id uuid NOT NULL,
    channel character varying(255) NOT NULL,
    type character varying(255) NOT NULL,
    title character varying(255) NOT NULL,
    body text,
    data text,
    is_read boolean DEFAULT false NOT NULL,
    is_sent boolean DEFAULT false NOT NULL,
    sent_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: projects; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.projects (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    created_by uuid,
    deleted_at timestamp(6) with time zone,
    tenant_id uuid NOT NULL,
    updated_at timestamp(6) with time zone,
    updated_by uuid,
    completed_sessions integer NOT NULL,
    description text,
    estimated_cost numeric(12,2) NOT NULL,
    status character varying(255) NOT NULL,
    title character varying(255) NOT NULL,
    total_paid numeric(12,2) NOT NULL,
    total_sessions integer NOT NULL,
    artist_id uuid NOT NULL,
    client_id uuid NOT NULL,
    location_id uuid,
    sketch_image character varying(255),
    CONSTRAINT projects_status_check CHECK (((status)::text = ANY ((ARRAY['IN_PROGRESS'::character varying, 'ON_HOLD'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: promotion_services; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.promotion_services (
    promotion_id uuid NOT NULL,
    service_id uuid NOT NULL
);


--
-- Name: promotions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.promotions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    name character varying(255) NOT NULL,
    description character varying(255),
    discount_type character varying(255) NOT NULL,
    discount_value numeric(10,2) NOT NULL,
    valid_from date,
    valid_to date,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    deleted_at timestamp with time zone
);


--
-- Name: requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.requests (
    id uuid NOT NULL,
    client_name character varying(255) NOT NULL,
    client_nickname character varying(255),
    converted_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone NOT NULL,
    instagram character varying(255),
    message text,
    phone character varying(255),
    replied_at timestamp(6) with time zone,
    source character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    tenant_id uuid NOT NULL,
    converted_client_id uuid,
    location_id uuid,
    staff_id uuid,
    consumer_user_id uuid,
    tattoo_timing character varying(30),
    tattoo_size character varying(30),
    body_zones text,
    is_cover_up boolean,
    idea text,
    reference_urls text,
    city character varying(50),
    contact_method character varying(20),
    contact_value character varying(255),
    CONSTRAINT requests_source_check CHECK (((source)::text = ANY ((ARRAY['INSTAGRAM'::character varying, 'TELEGRAM'::character varying, 'WEBSITE'::character varying, 'REFERRAL'::character varying, 'WALK_IN'::character varying, 'OTHER'::character varying])::text[]))),
    CONSTRAINT requests_status_check CHECK (((status)::text = ANY ((ARRAY['NEW'::character varying, 'REPLIED'::character varying, 'CONVERTED'::character varying, 'SPAM'::character varying])::text[])))
);


--
-- Name: role_permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role_permissions (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone,
    granted boolean NOT NULL,
    permission character varying(255) NOT NULL,
    role character varying(255) NOT NULL,
    tenant_id uuid NOT NULL,
    updated_at timestamp(6) with time zone,
    CONSTRAINT role_permissions_role_check CHECK (((role)::text = ANY ((ARRAY['OWNER'::character varying, 'ADMIN'::character varying, 'ARTIST'::character varying])::text[])))
);


--
-- Name: services; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.services (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    created_by uuid,
    deleted_at timestamp(6) with time zone,
    tenant_id uuid NOT NULL,
    updated_at timestamp(6) with time zone,
    updated_by uuid,
    color character varying(255) NOT NULL,
    description character varying(255),
    duration integer NOT NULL,
    is_active boolean NOT NULL,
    price numeric(10,2) NOT NULL,
    pricing_type character varying(255) NOT NULL,
    title character varying(255) NOT NULL,
    cost_price numeric(10,2),
    CONSTRAINT services_pricing_type_check CHECK (((pricing_type)::text = ANY ((ARRAY['FIXED'::character varying, 'HOURLY'::character varying, 'PROJECT'::character varying])::text[])))
);


--
-- Name: staff; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.staff (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    created_by uuid,
    deleted_at timestamp(6) with time zone,
    tenant_id uuid NOT NULL,
    updated_at timestamp(6) with time zone,
    updated_by uuid,
    auth_user_id character varying(255),
    avatar character varying(255),
    bio character varying(255),
    calendar_color character varying(255) NOT NULL,
    email character varying(255) NOT NULL,
    first_name character varying(255) NOT NULL,
    instagram character varying(255),
    last_name character varying(255) NOT NULL,
    phone character varying(255),
    role character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    google_access_token text,
    google_refresh_token text,
    google_calendar_id character varying(255),
    google_token_expires_at timestamp with time zone,
    google_calendar_email character varying(255),
    salary_type character varying(255) DEFAULT 'NONE'::character varying NOT NULL,
    salary_rate numeric(10,2),
    bank_details text,
    "position" character varying(255),
    birthday date,
    tax_id character varying(255),
    iban character varying(255),
    bank_card character varying(255),
    available_for_online_booking boolean DEFAULT true NOT NULL,
    is_public boolean DEFAULT false NOT NULL,
    hourly_rate numeric(10,2),
    studio_photo_url text,
    is_service_provider boolean DEFAULT true NOT NULL,
    account_status character varying(255) DEFAULT 'ACTIVE'::character varying NOT NULL,
    CONSTRAINT staff_role_check CHECK (((role)::text = ANY ((ARRAY['OWNER'::character varying, 'ADMIN'::character varying, 'ARTIST'::character varying])::text[]))),
    CONSTRAINT staff_status_check CHECK (((status)::text = ANY ((ARRAY['WORKING'::character varying, 'ON_VACATION'::character varying, 'SICK_LEAVE'::character varying, 'FIRED'::character varying])::text[])))
);


--
-- Name: staff_dont_do; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.staff_dont_do (
    staff_id uuid NOT NULL,
    item character varying(255) NOT NULL
);


--
-- Name: staff_faq; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.staff_faq (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    staff_id uuid NOT NULL,
    question text NOT NULL,
    answer text NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: staff_invite_locations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.staff_invite_locations (
    invite_id uuid NOT NULL,
    location_id uuid
);


--
-- Name: staff_invites; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.staff_invites (
    id uuid NOT NULL,
    accepted_at timestamp(6) with time zone,
    calendar_color character varying(255),
    created_at timestamp(6) with time zone NOT NULL,
    email character varying(255) NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    invited_by uuid NOT NULL,
    role character varying(255) NOT NULL,
    tenant_id uuid NOT NULL,
    token character varying(255) NOT NULL,
    is_service_provider boolean DEFAULT true NOT NULL,
    CONSTRAINT staff_invites_role_check CHECK (((role)::text = ANY ((ARRAY['OWNER'::character varying, 'ADMIN'::character varying, 'ARTIST'::character varying])::text[])))
);


--
-- Name: staff_locations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.staff_locations (
    staff_id uuid NOT NULL,
    location_id uuid NOT NULL
);


--
-- Name: staff_portfolio_images; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.staff_portfolio_images (
    staff_id uuid NOT NULL,
    image_url text NOT NULL
);


--
-- Name: staff_schedules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.staff_schedules (
    id uuid NOT NULL,
    day_of_week character varying(255) NOT NULL,
    end_time time(6) without time zone,
    is_working boolean NOT NULL,
    start_time time(6) without time zone,
    staff_id uuid NOT NULL,
    CONSTRAINT staff_schedules_day_of_week_check CHECK (((day_of_week)::text = ANY ((ARRAY['MONDAY'::character varying, 'TUESDAY'::character varying, 'WEDNESDAY'::character varying, 'THURSDAY'::character varying, 'FRIDAY'::character varying, 'SATURDAY'::character varying, 'SUNDAY'::character varying])::text[])))
);


--
-- Name: staff_specializations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.staff_specializations (
    staff_id uuid NOT NULL,
    specialization character varying(255)
);


--
-- Name: subscriptions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.subscriptions (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    current_period_end timestamp(6) with time zone,
    last_invoice_id character varying(255),
    monthly_price numeric(10,2),
    plan character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    tenant_id uuid NOT NULL,
    trial_ends_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone
);


--
-- Name: tattoo_styles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tattoo_styles (
    id bigint NOT NULL,
    slug character varying(50) NOT NULL,
    name character varying(100) NOT NULL,
    image_url text,
    image_urls text[] DEFAULT '{}'::text[],
    keywords text[] DEFAULT '{}'::text[],
    sort_order integer DEFAULT 0,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: tattoo_styles_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tattoo_styles_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tattoo_styles_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tattoo_styles_id_seq OWNED BY public.tattoo_styles.id;


--
-- Name: tattoos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tattoos (
    id bigint NOT NULL,
    source character varying(50) DEFAULT 'unsplash'::character varying NOT NULL,
    source_id character varying(255),
    image_url text NOT NULL,
    thumbnail_url text NOT NULL,
    width integer,
    height integer,
    blur_hash character varying(100),
    dominant_color character varying(7),
    author_name character varying(255),
    author_url text,
    description text,
    alt_description text,
    tags text[],
    embedding public.vector(1024),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    sort_order integer,
    staff_id uuid,
    status character varying(20) DEFAULT 'READY'::character varying NOT NULL,
    showcase boolean DEFAULT false NOT NULL
);


--
-- Name: tattoos_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tattoos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tattoos_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tattoos_id_seq OWNED BY public.tattoos.id;


--
-- Name: tenants; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tenants (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    currency character varying(255) NOT NULL,
    is_active boolean NOT NULL,
    language character varying(255) NOT NULL,
    logo character varying(255),
    name character varying(255) NOT NULL,
    subdomain character varying(255) NOT NULL,
    timezone character varying(255) NOT NULL,
    updated_at timestamp(6) with time zone,
    account_type character varying(255) DEFAULT 'STUDIO'::character varying NOT NULL
);


--
-- Name: transaction_category_configs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.transaction_category_configs (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    tenant_id uuid NOT NULL,
    category_key character varying(255) NOT NULL,
    label character varying(255) NOT NULL,
    color character varying(255),
    pl_type character varying(255) DEFAULT 'NEUTRAL'::character varying NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    is_default boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by uuid,
    updated_by uuid,
    deleted_at timestamp with time zone
);


--
-- Name: transactions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.transactions (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    created_by uuid,
    deleted_at timestamp(6) with time zone,
    tenant_id uuid NOT NULL,
    updated_at timestamp(6) with time zone,
    updated_by uuid,
    amount numeric(12,2) NOT NULL,
    card_amount numeric(12,2),
    cash_amount numeric(12,2),
    category character varying(255) NOT NULL,
    date timestamp(6) with time zone NOT NULL,
    description character varying(255),
    is_refunded boolean NOT NULL,
    original_transaction_id uuid,
    payment_method character varying(255) NOT NULL,
    payment_type character varying(255),
    receipt_number character varying(255),
    refund_reason character varying(255),
    refunded_amount numeric(12,2),
    tip_amount numeric(10,2),
    type character varying(255) NOT NULL,
    appointment_id uuid,
    location_id uuid NOT NULL,
    processed_by_id uuid,
    staff_id uuid,
    CONSTRAINT transactions_category_check CHECK (((category)::text = ANY ((ARRAY['SERVICE'::character varying, 'RENT'::character varying, 'SUPPLIES'::character varying, 'SALARY'::character varying, 'MERCH'::character varying, 'OTHER'::character varying])::text[]))),
    CONSTRAINT transactions_payment_method_check CHECK (((payment_method)::text = ANY (ARRAY['CASH'::text, 'CARD'::text, 'SPLIT'::text, 'MONOBANK'::text]))),
    CONSTRAINT transactions_payment_type_check CHECK (((payment_type)::text = ANY ((ARRAY['DEPOSIT'::character varying, 'SERVICE_PAYMENT'::character varying, 'REFUND'::character varying, 'TIP'::character varying])::text[]))),
    CONSTRAINT transactions_type_check CHECK (((type)::text = ANY ((ARRAY['INCOME'::character varying, 'EXPENSE'::character varying])::text[])))
);


--
-- Name: tattoo_styles id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tattoo_styles ALTER COLUMN id SET DEFAULT nextval('public.tattoo_styles_id_seq'::regclass);


--
-- Name: tattoos id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tattoos ALTER COLUMN id SET DEFAULT nextval('public.tattoos_id_seq'::regclass);


--
-- Name: ai_generations ai_generations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_generations
    ADD CONSTRAINT ai_generations_pkey PRIMARY KEY (id);


--
-- Name: appointments appointments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT appointments_pkey PRIMARY KEY (id);


--
-- Name: artist_service_pricing artist_service_pricing_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artist_service_pricing
    ADD CONSTRAINT artist_service_pricing_pkey PRIMARY KEY (id);


--
-- Name: audit_log audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_log
    ADD CONSTRAINT audit_log_pkey PRIMARY KEY (id);


--
-- Name: clients clients_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clients
    ADD CONSTRAINT clients_pkey PRIMARY KEY (id);


--
-- Name: company_settings company_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.company_settings
    ADD CONSTRAINT company_settings_pkey PRIMARY KEY (tenant_id);


--
-- Name: consumer_users consumer_users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.consumer_users
    ADD CONSTRAINT consumer_users_pkey PRIMARY KEY (id);


--
-- Name: device_tokens device_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.device_tokens
    ADD CONSTRAINT device_tokens_pkey PRIMARY KEY (id);


--
-- Name: device_tokens device_tokens_token_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.device_tokens
    ADD CONSTRAINT device_tokens_token_key UNIQUE (token);


--
-- Name: email_logs email_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_logs
    ADD CONSTRAINT email_logs_pkey PRIMARY KEY (id);


--
-- Name: gallery_photos gallery_photos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gallery_photos
    ADD CONSTRAINT gallery_photos_pkey PRIMARY KEY (id);


--
-- Name: leave_requests leave_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leave_requests
    ADD CONSTRAINT leave_requests_pkey PRIMARY KEY (id);


--
-- Name: locations locations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.locations
    ADD CONSTRAINT locations_pkey PRIMARY KEY (id);


--
-- Name: monobank_invoices monobank_invoices_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.monobank_invoices
    ADD CONSTRAINT monobank_invoices_pkey PRIMARY KEY (id);


--
-- Name: notifications notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);


--
-- Name: projects projects_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT projects_pkey PRIMARY KEY (id);


--
-- Name: promotions promotions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.promotions
    ADD CONSTRAINT promotions_pkey PRIMARY KEY (id);


--
-- Name: requests requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.requests
    ADD CONSTRAINT requests_pkey PRIMARY KEY (id);


--
-- Name: role_permissions role_permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_pkey PRIMARY KEY (id);


--
-- Name: services services_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.services
    ADD CONSTRAINT services_pkey PRIMARY KEY (id);


--
-- Name: staff_faq staff_faq_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.staff_faq
    ADD CONSTRAINT staff_faq_pkey PRIMARY KEY (id);


--
-- Name: staff_invites staff_invites_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.staff_invites
    ADD CONSTRAINT staff_invites_pkey PRIMARY KEY (id);


--
-- Name: staff_locations staff_locations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.staff_locations
    ADD CONSTRAINT staff_locations_pkey PRIMARY KEY (staff_id, location_id);


--
-- Name: staff staff_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.staff
    ADD CONSTRAINT staff_pkey PRIMARY KEY (id);


--
-- Name: staff_schedules staff_schedules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.staff_schedules
    ADD CONSTRAINT staff_schedules_pkey PRIMARY KEY (id);


--
-- Name: subscriptions subscriptions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscriptions
    ADD CONSTRAINT subscriptions_pkey PRIMARY KEY (id);


--
-- Name: tattoo_styles tattoo_styles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tattoo_styles
    ADD CONSTRAINT tattoo_styles_pkey PRIMARY KEY (id);


--
-- Name: tattoo_styles tattoo_styles_slug_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tattoo_styles
    ADD CONSTRAINT tattoo_styles_slug_key UNIQUE (slug);


--
-- Name: tattoos tattoos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tattoos
    ADD CONSTRAINT tattoos_pkey PRIMARY KEY (id);


--
-- Name: tattoos tattoos_source_source_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tattoos
    ADD CONSTRAINT tattoos_source_source_id_key UNIQUE (source, source_id);


--
-- Name: tenants tenants_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT tenants_pkey PRIMARY KEY (id);


--
-- Name: transaction_category_configs transaction_category_configs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaction_category_configs
    ADD CONSTRAINT transaction_category_configs_pkey PRIMARY KEY (id);


--
-- Name: transactions transactions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transactions
    ADD CONSTRAINT transactions_pkey PRIMARY KEY (id);


--
-- Name: device_tokens uk_device_token; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.device_tokens
    ADD CONSTRAINT uk_device_token UNIQUE (token);


--
-- Name: staff_invites ukb231dpch9u8jkdurqrsdptq0q; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.staff_invites
    ADD CONSTRAINT ukb231dpch9u8jkdurqrsdptq0q UNIQUE (token);


--
-- Name: appointments ukcve2br5f6do62n61okp2tdg28; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT ukcve2br5f6do62n61okp2tdg28 UNIQUE (consent_token);


--
-- Name: clients ukeswc5lgpwp0a1wtc6cmngn8lb; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clients
    ADD CONSTRAINT ukeswc5lgpwp0a1wtc6cmngn8lb UNIQUE (tenant_id, phone);


--
-- Name: subscriptions ukh38j9bo0d0l3xmlwtf7xx25iv; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscriptions
    ADD CONSTRAINT ukh38j9bo0d0l3xmlwtf7xx25iv UNIQUE (tenant_id);


--
-- Name: role_permissions uki5a4rct1qn0jvoma5q1nd86og; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT uki5a4rct1qn0jvoma5q1nd86og UNIQUE (tenant_id, role, permission);


--
-- Name: tenants ukowdadmekvhjx3lk2yt24s9pyw; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT ukowdadmekvhjx3lk2yt24s9pyw UNIQUE (subdomain);


--
-- Name: staff_schedules ukrpha0kfkrmv8nns8024m051wb; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.staff_schedules
    ADD CONSTRAINT ukrpha0kfkrmv8nns8024m051wb UNIQUE (staff_id, day_of_week);


--
-- Name: monobank_invoices uks4xmpa3rxvwdem19bxfaevntg; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.monobank_invoices
    ADD CONSTRAINT uks4xmpa3rxvwdem19bxfaevntg UNIQUE (monobank_invoice_id);


--
-- Name: artist_service_pricing uksihy78q6xf4gfq5g20x130u41; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artist_service_pricing
    ADD CONSTRAINT uksihy78q6xf4gfq5g20x130u41 UNIQUE (staff_id, service_id);


--
-- Name: transactions ukxidwnbx7m3ccgplaol2ynjly; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transactions
    ADD CONSTRAINT ukxidwnbx7m3ccgplaol2ynjly UNIQUE (receipt_number);


--
-- Name: idx_appointment_artist; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_appointment_artist ON public.appointments USING btree (artist_id);


--
-- Name: idx_appointment_client; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_appointment_client ON public.appointments USING btree (client_id);


--
-- Name: idx_appointment_location; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_appointment_location ON public.appointments USING btree (location_id);


--
-- Name: idx_appointment_service; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_appointment_service ON public.appointments USING btree (service_id);


--
-- Name: idx_appointment_start_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_appointment_start_time ON public.appointments USING btree (start_time);


--
-- Name: idx_appointment_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_appointment_status ON public.appointments USING btree (status);


--
-- Name: idx_appointment_tenant_deleted; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_appointment_tenant_deleted ON public.appointments USING btree (tenant_id, deleted_at);


--
-- Name: idx_appointment_tenant_start; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_appointment_tenant_start ON public.appointments USING btree (tenant_id, start_time);


--
-- Name: idx_asp_service; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_asp_service ON public.artist_service_pricing USING btree (service_id);


--
-- Name: idx_asp_staff; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_asp_staff ON public.artist_service_pricing USING btree (staff_id);


--
-- Name: idx_audit_actor; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_actor ON public.audit_log USING btree (actor_id);


--
-- Name: idx_audit_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_created ON public.audit_log USING btree (created_at);


--
-- Name: idx_audit_entity; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_entity ON public.audit_log USING btree (entity_type, entity_id);


--
-- Name: idx_audit_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_tenant ON public.audit_log USING btree (tenant_id);


--
-- Name: idx_client_location; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_client_location ON public.clients USING btree (location_id);


--
-- Name: idx_client_phone_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_client_phone_tenant ON public.clients USING btree (phone, tenant_id);


--
-- Name: idx_client_tenant_deleted; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_client_tenant_deleted ON public.clients USING btree (tenant_id, deleted_at);


--
-- Name: idx_device_tokens_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_device_tokens_user_id ON public.device_tokens USING btree (user_id);


--
-- Name: idx_device_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_device_user ON public.device_tokens USING btree (user_id);


--
-- Name: idx_notif_recipient; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notif_recipient ON public.notifications USING btree (recipient_id, is_read, created_at);


--
-- Name: idx_notif_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notif_tenant ON public.notifications USING btree (tenant_id, created_at);


--
-- Name: idx_project_artist; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_project_artist ON public.projects USING btree (artist_id);


--
-- Name: idx_project_client; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_project_client ON public.projects USING btree (client_id);


--
-- Name: idx_project_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_project_status ON public.projects USING btree (status);


--
-- Name: idx_project_tenant_deleted; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_project_tenant_deleted ON public.projects USING btree (tenant_id, deleted_at);


--
-- Name: idx_promotion_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_promotion_active ON public.promotions USING btree (is_active);


--
-- Name: idx_promotion_tenant_deleted; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_promotion_tenant_deleted ON public.promotions USING btree (tenant_id, deleted_at);


--
-- Name: idx_request_location; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_request_location ON public.requests USING btree (location_id);


--
-- Name: idx_request_tenant_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_request_tenant_created ON public.requests USING btree (tenant_id, created_at);


--
-- Name: idx_request_tenant_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_request_tenant_status ON public.requests USING btree (tenant_id, status);


--
-- Name: idx_schedule_staff; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_schedule_staff ON public.staff_schedules USING btree (staff_id);


--
-- Name: idx_staff_auth_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_staff_auth_user ON public.staff USING btree (auth_user_id);


--
-- Name: idx_staff_dont_do_staff_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_staff_dont_do_staff_id ON public.staff_dont_do USING btree (staff_id);


--
-- Name: idx_staff_email_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_staff_email_tenant ON public.staff USING btree (email, tenant_id);


--
-- Name: idx_staff_faq_staff_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_staff_faq_staff_id ON public.staff_faq USING btree (staff_id, sort_order);


--
-- Name: idx_staff_portfolio_staff_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_staff_portfolio_staff_id ON public.staff_portfolio_images USING btree (staff_id);


--
-- Name: idx_staff_tenant_deleted; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_staff_tenant_deleted ON public.staff USING btree (tenant_id, deleted_at);


--
-- Name: idx_tattoos_source_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tattoos_source_id ON public.tattoos USING btree (source, source_id);


--
-- Name: idx_tattoos_staff_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tattoos_staff_id ON public.tattoos USING btree (staff_id);


--
-- Name: idx_tcc_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tcc_tenant ON public.transaction_category_configs USING btree (tenant_id, deleted_at);


--
-- Name: idx_transaction_appointment; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_transaction_appointment ON public.transactions USING btree (appointment_id);


--
-- Name: idx_transaction_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_transaction_date ON public.transactions USING btree (date);


--
-- Name: idx_transaction_staff; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_transaction_staff ON public.transactions USING btree (staff_id);


--
-- Name: idx_transaction_tenant_deleted; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_transaction_tenant_deleted ON public.transactions USING btree (tenant_id, deleted_at);


--
-- Name: idx_transaction_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_transaction_type ON public.transactions USING btree (type);


--
-- Name: ai_generations ai_generations_consumer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_generations
    ADD CONSTRAINT ai_generations_consumer_id_fkey FOREIGN KEY (user_id) REFERENCES public.consumer_users(id);


--
-- Name: consumer_favorite_generations consumer_favorite_generations_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.consumer_favorite_generations
    ADD CONSTRAINT consumer_favorite_generations_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.consumer_users(id) ON DELETE CASCADE;


--
-- Name: consumer_saved_artists consumer_saved_artists_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.consumer_saved_artists
    ADD CONSTRAINT consumer_saved_artists_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.consumer_users(id) ON DELETE CASCADE;


--
-- Name: consumer_saved_tattoos consumer_saved_tattoos_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.consumer_saved_tattoos
    ADD CONSTRAINT consumer_saved_tattoos_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.consumer_users(id) ON DELETE CASCADE;


--
-- Name: gallery_photos fk1ufmfawpj1ksjmlalmyf3gaw; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gallery_photos
    ADD CONSTRAINT fk1ufmfawpj1ksjmlalmyf3gaw FOREIGN KEY (appointment_id) REFERENCES public.appointments(id);


--
-- Name: staff_locations fk31tmht9c798jfsrmlkqtlsgxb; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.staff_locations
    ADD CONSTRAINT fk31tmht9c798jfsrmlkqtlsgxb FOREIGN KEY (location_id) REFERENCES public.locations(id);


--
-- Name: gallery_photos fk35n48k3nw5n1bj6n2lw5uk40j; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gallery_photos
    ADD CONSTRAINT fk35n48k3nw5n1bj6n2lw5uk40j FOREIGN KEY (project_id) REFERENCES public.projects(id);


--
-- Name: appointments fk5iltr7k9pows18hk8nc101vc1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT fk5iltr7k9pows18hk8nc101vc1 FOREIGN KEY (service_id) REFERENCES public.services(id);


--
-- Name: requests fk6ih4c1c2aq6abdobiyhqw98eg; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.requests
    ADD CONSTRAINT fk6ih4c1c2aq6abdobiyhqw98eg FOREIGN KEY (converted_client_id) REFERENCES public.clients(id);


--
-- Name: leave_requests fk6lv44kcskol722iljkawrnvoc; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leave_requests
    ADD CONSTRAINT fk6lv44kcskol722iljkawrnvoc FOREIGN KEY (staff_id) REFERENCES public.staff(id);


--
-- Name: staff_schedules fk7d4s546ly10viqgid7nk6vtmr; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.staff_schedules
    ADD CONSTRAINT fk7d4s546ly10viqgid7nk6vtmr FOREIGN KEY (staff_id) REFERENCES public.staff(id);


--
-- Name: appointments fkas2dcydrqfldiov73dofbohvx; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT fkas2dcydrqfldiov73dofbohvx FOREIGN KEY (artist_id) REFERENCES public.staff(id);


--
-- Name: staff_specializations fkeofec05144r4xtcslyailusd3; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.staff_specializations
    ADD CONSTRAINT fkeofec05144r4xtcslyailusd3 FOREIGN KEY (staff_id) REFERENCES public.staff(id);


--
-- Name: staff_locations fkep7b1qus54ujuduo2s3ek8bdj; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.staff_locations
    ADD CONSTRAINT fkep7b1qus54ujuduo2s3ek8bdj FOREIGN KEY (staff_id) REFERENCES public.staff(id);


--
-- Name: leave_requests fkepnxjrq4n9o4iqkcnpwnul85j; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leave_requests
    ADD CONSTRAINT fkepnxjrq4n9o4iqkcnpwnul85j FOREIGN KEY (approved_by) REFERENCES public.staff(id);


--
-- Name: appointments fkfbl6cciquyyvv5s1e31qmflkb; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT fkfbl6cciquyyvv5s1e31qmflkb FOREIGN KEY (client_id) REFERENCES public.clients(id);


--
-- Name: clients fkffddd82fcsmbb45nf4sxb9uhx; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clients
    ADD CONSTRAINT fkffddd82fcsmbb45nf4sxb9uhx FOREIGN KEY (location_id) REFERENCES public.locations(id);


--
-- Name: transactions fkfner4cly0tqxmdut6udfxcb2b; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transactions
    ADD CONSTRAINT fkfner4cly0tqxmdut6udfxcb2b FOREIGN KEY (staff_id) REFERENCES public.staff(id);


--
-- Name: transactions fkg1x27q97od3agkbkl1mvcggtt; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transactions
    ADD CONSTRAINT fkg1x27q97od3agkbkl1mvcggtt FOREIGN KEY (appointment_id) REFERENCES public.appointments(id);


--
-- Name: company_settings fkh5qu3xft4w65q9q15qy55jnye; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.company_settings
    ADD CONSTRAINT fkh5qu3xft4w65q9q15qy55jnye FOREIGN KEY (tenant_id) REFERENCES public.tenants(id);


--
-- Name: appointments fkih5p0401n78lw5v48csf7eo4s; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT fkih5p0401n78lw5v48csf7eo4s FOREIGN KEY (project_id) REFERENCES public.projects(id);


--
-- Name: projects fkksdiyuily2f4ca2y53k07pmq; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT fkksdiyuily2f4ca2y53k07pmq FOREIGN KEY (client_id) REFERENCES public.clients(id);


--
-- Name: projects fkmyo34sdcdpw93cu93aegbnvrk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT fkmyo34sdcdpw93cu93aegbnvrk FOREIGN KEY (artist_id) REFERENCES public.staff(id);


--
-- Name: artist_service_pricing fkn9ua94unwsli0hsr8xtklh4bn; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artist_service_pricing
    ADD CONSTRAINT fkn9ua94unwsli0hsr8xtklh4bn FOREIGN KEY (service_id) REFERENCES public.services(id);


--
-- Name: transactions fknkd20fxt704n09r9k1p31urlb; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transactions
    ADD CONSTRAINT fknkd20fxt704n09r9k1p31urlb FOREIGN KEY (processed_by_id) REFERENCES public.staff(id);


--
-- Name: requests fknoks84e3vv85q7pt8v3ejv6xs; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.requests
    ADD CONSTRAINT fknoks84e3vv85q7pt8v3ejv6xs FOREIGN KEY (location_id) REFERENCES public.locations(id);


--
-- Name: artist_service_pricing fkny638e346cwjle4vjg76vdro; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.artist_service_pricing
    ADD CONSTRAINT fkny638e346cwjle4vjg76vdro FOREIGN KEY (staff_id) REFERENCES public.staff(id);


--
-- Name: client_tags fkqb592kwf3yy09e0u2bsft2tag; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.client_tags
    ADD CONSTRAINT fkqb592kwf3yy09e0u2bsft2tag FOREIGN KEY (client_id) REFERENCES public.clients(id);


--
-- Name: staff_invite_locations fkrbylog3symb60wh7imfu93yt3; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.staff_invite_locations
    ADD CONSTRAINT fkrbylog3symb60wh7imfu93yt3 FOREIGN KEY (invite_id) REFERENCES public.staff_invites(id);


--
-- Name: projects fkrdf97scpatk3kkck4svqaaksf; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT fkrdf97scpatk3kkck4svqaaksf FOREIGN KEY (location_id) REFERENCES public.locations(id);


--
-- Name: transactions fks9u3i6al14jdrpqf1fth2fpao; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transactions
    ADD CONSTRAINT fks9u3i6al14jdrpqf1fth2fpao FOREIGN KEY (location_id) REFERENCES public.locations(id);


--
-- Name: appointments fksorylc1v099qpxex8nfwuvlog; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appointments
    ADD CONSTRAINT fksorylc1v099qpxex8nfwuvlog FOREIGN KEY (location_id) REFERENCES public.locations(id);


--
-- Name: client_medical_conditions fktlfrqil1gq0kin7p104s4s03p; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.client_medical_conditions
    ADD CONSTRAINT fktlfrqil1gq0kin7p104s4s03p FOREIGN KEY (client_id) REFERENCES public.clients(id);


--
-- Name: promotion_services promotion_services_promotion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.promotion_services
    ADD CONSTRAINT promotion_services_promotion_id_fkey FOREIGN KEY (promotion_id) REFERENCES public.promotions(id) ON DELETE CASCADE;


--
-- Name: requests requests_staff_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.requests
    ADD CONSTRAINT requests_staff_id_fkey FOREIGN KEY (staff_id) REFERENCES public.staff(id);


--
-- Name: staff_dont_do staff_dont_do_staff_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.staff_dont_do
    ADD CONSTRAINT staff_dont_do_staff_id_fkey FOREIGN KEY (staff_id) REFERENCES public.staff(id) ON DELETE CASCADE;


--
-- Name: staff_faq staff_faq_staff_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.staff_faq
    ADD CONSTRAINT staff_faq_staff_id_fkey FOREIGN KEY (staff_id) REFERENCES public.staff(id) ON DELETE CASCADE;


--
-- Name: staff_portfolio_images staff_portfolio_images_staff_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.staff_portfolio_images
    ADD CONSTRAINT staff_portfolio_images_staff_id_fkey FOREIGN KEY (staff_id) REFERENCES public.staff(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--


