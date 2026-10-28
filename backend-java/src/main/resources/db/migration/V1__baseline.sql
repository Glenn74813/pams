-- Baseline: schema of the Laravel-era database as of 2026-10-28 (117 Laravel migrations).
-- On the existing database Flyway marks this as applied (baseline-on-migrate); on a fresh database it creates the schema.
--
-- PostgreSQL database dump
--

-- Dumped from database version 18.6 (Ubuntu 18.6-1.pgdg26.04+2)
-- Dumped by pg_dump version 18.6 (Ubuntu 18.6-1.pgdg26.04+2)

--
-- Name: apcpi_assessments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.apcpi_assessments (
    id bigint NOT NULL,
    fiscal_year integer NOT NULL,
    status character varying(255) DEFAULT 'draft'::character varying NOT NULL,
    conducted_by bigint,
    finalized_at timestamp(0) without time zone,
    remarks text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

--
-- Name: apcpi_assessments_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.apcpi_assessments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: apcpi_assessments_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.apcpi_assessments_id_seq OWNED BY public.apcpi_assessments.id;

--
-- Name: apcpi_scores; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.apcpi_scores (
    id bigint NOT NULL,
    apcpi_assessment_id bigint NOT NULL,
    indicator_key character varying(255) NOT NULL,
    score smallint DEFAULT '0'::smallint NOT NULL,
    remarks text,
    means_of_verification text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

--
-- Name: apcpi_scores_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.apcpi_scores_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: apcpi_scores_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.apcpi_scores_id_seq OWNED BY public.apcpi_scores.id;

--
-- Name: app_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_items (
    id bigint NOT NULL,
    app_id bigint NOT NULL,
    ppmp_id bigint,
    department_id bigint,
    general_description character varying(255) NOT NULL,
    procurement_mode character varying(255) DEFAULT 'Competitive Bidding'::character varying NOT NULL,
    quantity integer DEFAULT 1 NOT NULL,
    estimated_budget numeric(15,2) DEFAULT '0'::numeric NOT NULL,
    utilized_amount numeric(15,2) DEFAULT '0'::numeric NOT NULL,
    schedule character varying(255),
    fund_source character varying(255),
    status character varying(255) DEFAULT 'planned'::character varying NOT NULL,
    remarks text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    unit character varying(255),
    specifications text,
    procurement_category character varying(100) DEFAULT 'Goods'::character varying NOT NULL
);

--
-- Name: app_items_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.app_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: app_items_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.app_items_id_seq OWNED BY public.app_items.id;

--
-- Name: apps; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.apps (
    id bigint NOT NULL,
    app_number character varying(255) NOT NULL,
    fiscal_year smallint NOT NULL,
    title character varying(255) NOT NULL,
    status character varying(255) DEFAULT 'draft'::character varying NOT NULL,
    total_budget numeric(18,2) DEFAULT '0'::numeric NOT NULL,
    prepared_by bigint,
    approved_by bigint,
    approved_at timestamp(0) without time zone,
    remarks text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    deleted_at timestamp(0) without time zone
);

--
-- Name: apps_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.apps_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: apps_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.apps_id_seq OWNED BY public.apps.id;

--
-- Name: audit_trails; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_trails (
    id bigint NOT NULL,
    user_id bigint,
    user_name character varying(255),
    user_role character varying(255),
    action character varying(255) NOT NULL,
    auditable_type character varying(255),
    auditable_id bigint,
    description text,
    old_values json,
    new_values json,
    ip_address character varying(45),
    user_agent character varying(255),
    url character varying(255),
    method character varying(10),
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

--
-- Name: audit_trails_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.audit_trails_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: audit_trails_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.audit_trails_id_seq OWNED BY public.audit_trails.id;

--
-- Name: bac_evaluations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bac_evaluations (
    id bigint NOT NULL,
    purchase_request_id bigint,
    bac_meeting_id bigint,
    supplier_id bigint,
    stage character varying(255) NOT NULL,
    result character varying(255) DEFAULT 'pending'::character varying NOT NULL,
    bid_amount numeric(15,2),
    score numeric(8,2),
    evaluated_by bigint,
    evaluated_at timestamp(0) without time zone,
    remarks text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

--
-- Name: bac_evaluations_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.bac_evaluations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: bac_evaluations_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.bac_evaluations_id_seq OWNED BY public.bac_evaluations.id;

--
-- Name: bac_meeting_attendees; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bac_meeting_attendees (
    id bigint NOT NULL,
    bac_meeting_id bigint NOT NULL,
    user_id bigint,
    name character varying(255) NOT NULL,
    committee_role character varying(255),
    present boolean DEFAULT true NOT NULL,
    remarks text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

--
-- Name: bac_meeting_attendees_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.bac_meeting_attendees_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: bac_meeting_attendees_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.bac_meeting_attendees_id_seq OWNED BY public.bac_meeting_attendees.id;

--
-- Name: bac_meetings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bac_meetings (
    id bigint NOT NULL,
    meeting_number character varying(255) NOT NULL,
    title character varying(255) NOT NULL,
    meeting_date timestamp(0) without time zone NOT NULL,
    venue character varying(255),
    agenda text,
    status character varying(255) DEFAULT 'scheduled'::character varying NOT NULL,
    minutes text,
    called_by bigint,
    remarks text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    deleted_at timestamp(0) without time zone
);

--
-- Name: bac_meetings_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.bac_meetings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: bac_meetings_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.bac_meetings_id_seq OWNED BY public.bac_meetings.id;

--
-- Name: bac_members; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bac_members (
    id bigint NOT NULL,
    user_id bigint,
    full_name character varying(255) NOT NULL,
    "position" character varying(255),
    office character varying(255),
    designation character varying(255) DEFAULT 'member'::character varying NOT NULL,
    appointment_order_no character varying(255),
    appointment_date date,
    term_start date,
    term_end date,
    is_active boolean DEFAULT true NOT NULL,
    sequence integer DEFAULT 0 NOT NULL,
    remarks text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

--
-- Name: bac_members_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.bac_members_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: bac_members_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.bac_members_id_seq OWNED BY public.bac_members.id;

--
-- Name: bac_resolutions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bac_resolutions (
    id bigint NOT NULL,
    resolution_number character varying(255) NOT NULL,
    title character varying(255) NOT NULL,
    bac_meeting_id bigint,
    purchase_request_id bigint,
    resolution_type character varying(255) DEFAULT 'other'::character varying NOT NULL,
    content text,
    status character varying(255) DEFAULT 'draft'::character varying NOT NULL,
    prepared_by bigint,
    approved_by bigint,
    approved_at timestamp(0) without time zone,
    remarks text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    deleted_at timestamp(0) without time zone
);

--
-- Name: bac_resolutions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.bac_resolutions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: bac_resolutions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.bac_resolutions_id_seq OWNED BY public.bac_resolutions.id;

--
-- Name: bid_observers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bid_observers (
    id bigint NOT NULL,
    bidding_id bigint NOT NULL,
    name character varying(255) NOT NULL,
    organization character varying(255),
    observer_type character varying(255) DEFAULT 'COA'::character varying NOT NULL,
    invited boolean DEFAULT true NOT NULL,
    invited_date date,
    attended boolean DEFAULT false NOT NULL,
    remarks text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

--
-- Name: bid_observers_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.bid_observers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: bid_observers_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.bid_observers_id_seq OWNED BY public.bid_observers.id;

--
-- Name: bid_protests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bid_protests (
    id bigint NOT NULL,
    bidding_id bigint NOT NULL,
    protest_type character varying(255) DEFAULT 'Request for Reconsideration'::character varying NOT NULL,
    supplier_id bigint,
    filed_by character varying(255),
    grounds text NOT NULL,
    filed_date date NOT NULL,
    protest_fee numeric(18,2),
    status character varying(255) DEFAULT 'filed'::character varying NOT NULL,
    resolution text,
    resolved_date date,
    created_by bigint,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

--
-- Name: bid_protests_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.bid_protests_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: bid_protests_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.bid_protests_id_seq OWNED BY public.bid_protests.id;

--
-- Name: bidding_bidder_scores; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bidding_bidder_scores (
    id bigint NOT NULL,
    bidding_bidder_id bigint NOT NULL,
    bidding_criterion_id bigint NOT NULL,
    score numeric(6,2) DEFAULT '0'::numeric NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

--
-- Name: bidding_bidder_scores_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.bidding_bidder_scores_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: bidding_bidder_scores_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.bidding_bidder_scores_id_seq OWNED BY public.bidding_bidder_scores.id;

--
-- Name: bidding_bidders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bidding_bidders (
    id bigint NOT NULL,
    bidding_id bigint NOT NULL,
    supplier_id bigint NOT NULL,
    bid_amount numeric(18,2),
    eligibility_result character varying(20) DEFAULT 'pending'::character varying NOT NULL,
    technical_result character varying(20) DEFAULT 'pending'::character varying NOT NULL,
    financial_result character varying(20) DEFAULT 'pending'::character varying NOT NULL,
    post_qual_result character varying(20) DEFAULT 'pending'::character varying NOT NULL,
    is_winner boolean DEFAULT false NOT NULL,
    rank integer,
    remarks text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    technical_score numeric(6,2),
    bid_security_amount numeric(18,2),
    bid_security_form character varying(255),
    bid_security_posted boolean DEFAULT false NOT NULL,
    eligibility_checklist json
);

--
-- Name: bidding_bidders_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.bidding_bidders_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: bidding_bidders_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.bidding_bidders_id_seq OWNED BY public.bidding_bidders.id;

--
-- Name: bidding_criteria; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bidding_criteria (
    id bigint NOT NULL,
    bidding_id bigint NOT NULL,
    name character varying(255) NOT NULL,
    weight numeric(6,2) DEFAULT '0'::numeric NOT NULL,
    is_environmental boolean DEFAULT false NOT NULL,
    sequence integer DEFAULT 0 NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

--
-- Name: bidding_criteria_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.bidding_criteria_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: bidding_criteria_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.bidding_criteria_id_seq OWNED BY public.bidding_criteria.id;

--
-- Name: bidding_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bidding_items (
    id bigint NOT NULL,
    bidding_id bigint NOT NULL,
    purchase_request_item_id bigint,
    general_description character varying(255) NOT NULL,
    unit character varying(255),
    quantity integer DEFAULT 1 NOT NULL,
    abc_unit_cost numeric(15,2) DEFAULT '0'::numeric NOT NULL,
    remarks text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    specifications text
);

--
-- Name: bidding_items_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.bidding_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: bidding_items_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.bidding_items_id_seq OWNED BY public.bidding_items.id;

--
-- Name: biddings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.biddings (
    id bigint NOT NULL,
    bidding_number character varying(255) NOT NULL,
    title character varying(255) NOT NULL,
    purchase_request_id bigint,
    abc numeric(18,2) DEFAULT '0'::numeric NOT NULL,
    status character varying(30) DEFAULT 'draft'::character varying NOT NULL,
    pre_proc_date timestamp(0) without time zone,
    pre_proc_notes text,
    itb_reference character varying(255),
    itb_posted_at date,
    itb_publication text,
    submission_deadline timestamp(0) without time zone,
    bid_opening_date timestamp(0) without time zone,
    awarded_supplier_id bigint,
    awarded_amount numeric(18,2),
    noa_number character varying(255),
    noa_date date,
    ntp_number character varying(255),
    ntp_date date,
    contract_recommendation text,
    failure_reason text,
    prepared_by bigint,
    remarks text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    deleted_at timestamp(0) without time zone,
    evaluation_method character varying(10) DEFAULT 'price'::character varying NOT NULL,
    price_weight numeric(5,2) DEFAULT '100'::numeric NOT NULL,
    technical_weight numeric(5,2) DEFAULT '0'::numeric NOT NULL,
    awarded_at timestamp(0) without time zone,
    delivery_term character varying(255),
    failed_count integer DEFAULT 0 NOT NULL,
    bac_resolution_id bigint,
    delivery_lead_days integer
);

--
-- Name: biddings_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.biddings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: biddings_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.biddings_id_seq OWNED BY public.biddings.id;

--
-- Name: categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.categories (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    parent_id bigint,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    is_leaf boolean DEFAULT true NOT NULL
);

--
-- Name: categories_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.categories_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: categories_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.categories_id_seq OWNED BY public.categories.id;

--
-- Name: contract_amendments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.contract_amendments (
    id bigint NOT NULL,
    contract_id bigint NOT NULL,
    amendment_number integer NOT NULL,
    title character varying(255) NOT NULL,
    description text,
    amount_change numeric(18,2) DEFAULT '0'::numeric NOT NULL,
    new_end_date date,
    status character varying(20) DEFAULT 'draft'::character varying NOT NULL,
    prepared_by bigint,
    approved_by bigint,
    approved_at timestamp(0) without time zone,
    remarks text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

--
-- Name: contract_amendments_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.contract_amendments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: contract_amendments_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.contract_amendments_id_seq OWNED BY public.contract_amendments.id;

--
-- Name: contract_securities; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.contract_securities (
    id bigint NOT NULL,
    contract_id bigint NOT NULL,
    security_type character varying(255) NOT NULL,
    form character varying(255),
    reference_number character varying(255),
    amount numeric(18,2) DEFAULT '0'::numeric NOT NULL,
    percentage numeric(6,3),
    posted_date date,
    validity_date date,
    status character varying(255) DEFAULT 'posted'::character varying NOT NULL,
    remarks text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

--
-- Name: contract_securities_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.contract_securities_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: contract_securities_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.contract_securities_id_seq OWNED BY public.contract_securities.id;

--
-- Name: contracts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.contracts (
    id bigint NOT NULL,
    contract_number character varying(255) NOT NULL,
    title character varying(255) NOT NULL,
    supplier_id bigint NOT NULL,
    contract_type character varying(50) DEFAULT 'Goods'::character varying NOT NULL,
    purchase_request_id bigint,
    bidding_id bigint,
    rfq_id bigint,
    purchase_order_id bigint,
    amount numeric(18,2) DEFAULT '0'::numeric NOT NULL,
    status character varying(20) DEFAULT 'draft'::character varying NOT NULL,
    start_date date,
    end_date date,
    signed_date date,
    description text,
    terms text,
    prepared_by bigint,
    remarks text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    deleted_at timestamp(0) without time zone,
    ld_basis_amount numeric(18,2),
    ld_days_delayed integer DEFAULT 0 NOT NULL,
    ld_rate numeric(8,5) DEFAULT 0.001 NOT NULL,
    ld_amount numeric(18,2) DEFAULT '0'::numeric NOT NULL,
    ld_remarks text,
    ld_as_of_date date,
    delivery_lead_days integer
);

--
-- Name: contracts_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.contracts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: contracts_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.contracts_id_seq OWNED BY public.contracts.id;

--
-- Name: delivery_extensions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.delivery_extensions (
    id bigint NOT NULL,
    contract_id bigint NOT NULL,
    requested_days integer NOT NULL,
    approved_days integer,
    reason text NOT NULL,
    letter_path character varying(255),
    status character varying(255) DEFAULT 'pending'::character varying NOT NULL,
    requested_date date NOT NULL,
    approved_by bigint,
    approved_at timestamp(0) without time zone,
    remarks text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

--
-- Name: delivery_extensions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.delivery_extensions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: delivery_extensions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.delivery_extensions_id_seq OWNED BY public.delivery_extensions.id;

--
-- Name: departments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.departments (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    code character varying(255) NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

--
-- Name: departments_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.departments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: departments_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.departments_id_seq OWNED BY public.departments.id;

--
-- Name: disposal_notifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.disposal_notifications (
    id bigint NOT NULL,
    item_id bigint NOT NULL,
    maturity_date date NOT NULL,
    notified_at timestamp(0) without time zone,
    status character varying(255) DEFAULT 'pending'::character varying NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    CONSTRAINT disposal_notifications_status_check CHECK (((status)::text = ANY ((ARRAY['pending'::character varying, 'notified'::character varying, 'disposed'::character varying])::text[])))
);

--
-- Name: disposal_notifications_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.disposal_notifications_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: disposal_notifications_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.disposal_notifications_id_seq OWNED BY public.disposal_notifications.id;

--
-- Name: disposal_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.disposal_requests (
    id bigint NOT NULL,
    disposal_number character varying(255) NOT NULL,
    property_id bigint,
    requested_by bigint NOT NULL,
    request_date date NOT NULL,
    disposal_type character varying(255) DEFAULT 'condemnation'::character varying NOT NULL,
    status character varying(255) DEFAULT 'draft'::character varying NOT NULL,
    reason text,
    remarks text,
    approved_by bigint,
    approval_date date,
    disposal_date date,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    deleted_at timestamp(0) without time zone,
    disposal_category character varying(255) DEFAULT 'property'::character varying NOT NULL,
    item_id bigint,
    quantity integer,
    unit_cost numeric(18,2),
    CONSTRAINT disposal_requests_disposal_type_check CHECK (((disposal_type)::text = ANY ((ARRAY['condemnation'::character varying, 'donation'::character varying, 'sale'::character varying, 'destruction'::character varying, 'transfer'::character varying])::text[]))),
    CONSTRAINT disposal_requests_status_check CHECK (((status)::text = ANY ((ARRAY['draft'::character varying, 'submitted'::character varying, 'approved'::character varying, 'rejected'::character varying, 'completed'::character varying])::text[])))
);

--
-- Name: disposal_requests_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.disposal_requests_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: disposal_requests_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.disposal_requests_id_seq OWNED BY public.disposal_requests.id;

--
-- Name: document_logos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.document_logos (
    id bigint NOT NULL,
    path character varying(255) NOT NULL,
    "position" character varying(255) DEFAULT 'top-left'::character varying NOT NULL,
    sequence integer DEFAULT 0 NOT NULL,
    label character varying(255),
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

--
-- Name: document_logos_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.document_logos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: document_logos_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.document_logos_id_seq OWNED BY public.document_logos.id;

--
-- Name: document_versions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.document_versions (
    id bigint NOT NULL,
    document_id bigint NOT NULL,
    version_number integer NOT NULL,
    file_path character varying(255) NOT NULL,
    original_filename character varying(255) NOT NULL,
    mime_type character varying(255),
    file_size bigint DEFAULT '0'::bigint NOT NULL,
    remarks text,
    uploaded_by bigint,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

--
-- Name: document_versions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.document_versions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: document_versions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.document_versions_id_seq OWNED BY public.document_versions.id;

--
-- Name: documents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.documents (
    id bigint NOT NULL,
    title character varying(255) NOT NULL,
    document_number character varying(255),
    type character varying(255) NOT NULL,
    category character varying(255),
    description text,
    documentable_type character varying(255),
    documentable_id bigint,
    status character varying(255) DEFAULT 'active'::character varying NOT NULL,
    current_version integer DEFAULT 0 NOT NULL,
    uploaded_by bigint,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    deleted_at timestamp(0) without time zone
);

--
-- Name: documents_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.documents_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: documents_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.documents_id_seq OWNED BY public.documents.id;

--
-- Name: electronic_signatures; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.electronic_signatures (
    id bigint NOT NULL,
    signable_type character varying(255) NOT NULL,
    signable_id bigint NOT NULL,
    purpose character varying(255) NOT NULL,
    user_id bigint,
    signatory_name character varying(255) NOT NULL,
    designation character varying(255),
    signed_at timestamp(0) without time zone NOT NULL,
    signature_hash character varying(64) NOT NULL,
    remarks text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    signature_path character varying(255)
);

--
-- Name: electronic_signatures_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.electronic_signatures_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: electronic_signatures_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.electronic_signatures_id_seq OWNED BY public.electronic_signatures.id;

--
-- Name: framework_calloffs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.framework_calloffs (
    id bigint NOT NULL,
    contract_id bigint NOT NULL,
    calloff_number character varying(255) NOT NULL,
    title character varying(255) NOT NULL,
    supplier_id bigint,
    amount numeric(18,2) DEFAULT '0'::numeric NOT NULL,
    calloff_date date,
    status character varying(255) DEFAULT 'draft'::character varying NOT NULL,
    remarks text,
    created_by bigint,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

--
-- Name: framework_calloffs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.framework_calloffs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: framework_calloffs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.framework_calloffs_id_seq OWNED BY public.framework_calloffs.id;

--
-- Name: gate_passes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.gate_passes (
    id bigint NOT NULL,
    gate_pass_number character varying(255) NOT NULL,
    property_id bigint NOT NULL,
    released_by bigint NOT NULL,
    received_by character varying(255) NOT NULL,
    purpose text NOT NULL,
    date_released date NOT NULL,
    expected_return_date date,
    actual_return_date date,
    condition_on_release character varying(255) DEFAULT 'good'::character varying NOT NULL,
    condition_on_return character varying(255),
    status character varying(255) DEFAULT 'released'::character varying NOT NULL,
    remarks text,
    returned_by character varying(255),
    received_back_by bigint,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    deleted_at timestamp(0) without time zone,
    CONSTRAINT gate_passes_condition_on_release_check CHECK (((condition_on_release)::text = ANY ((ARRAY['good'::character varying, 'fair'::character varying, 'poor'::character varying, 'new'::character varying, 'excellent'::character varying])::text[]))),
    CONSTRAINT gate_passes_condition_on_return_check CHECK (((condition_on_return)::text = ANY ((ARRAY['good'::character varying, 'fair'::character varying, 'poor'::character varying, 'damaged'::character varying, 'lost'::character varying])::text[]))),
    CONSTRAINT gate_passes_status_check CHECK (((status)::text = ANY ((ARRAY['released'::character varying, 'returned'::character varying, 'overdue'::character varying])::text[])))
);

--
-- Name: gate_passes_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.gate_passes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: gate_passes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.gate_passes_id_seq OWNED BY public.gate_passes.id;

--
-- Name: iar_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.iar_items (
    id bigint NOT NULL,
    inspection_acceptance_report_id bigint NOT NULL,
    purchase_order_item_id bigint,
    description character varying(255) NOT NULL,
    unit character varying(255),
    quantity_ordered integer DEFAULT 0 NOT NULL,
    quantity_received integer DEFAULT 0 NOT NULL,
    quantity_accepted integer DEFAULT 0 NOT NULL,
    quantity_rejected integer DEFAULT 0 NOT NULL,
    status character varying(20) DEFAULT 'accepted'::character varying NOT NULL,
    remarks text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    specifications text,
    item_id bigint
);

--
-- Name: iar_items_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.iar_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: iar_items_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.iar_items_id_seq OWNED BY public.iar_items.id;

--
-- Name: inspection_acceptance_reports; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inspection_acceptance_reports (
    id bigint NOT NULL,
    iar_number character varying(255) NOT NULL,
    purchase_order_id bigint NOT NULL,
    delivery_date date NOT NULL,
    inspection_date date NOT NULL,
    inspected_by bigint NOT NULL,
    status character varying(255) DEFAULT 'passed'::character varying NOT NULL,
    total_items_ordered integer DEFAULT 0 NOT NULL,
    total_items_received integer DEFAULT 0 NOT NULL,
    total_items_accepted integer DEFAULT 0 NOT NULL,
    total_items_rejected integer DEFAULT 0 NOT NULL,
    observations text,
    remarks text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    acceptance_status character varying(255) DEFAULT 'pending'::character varying NOT NULL,
    accepted_by bigint,
    accepted_at timestamp(0) without time zone,
    CONSTRAINT inspection_acceptance_reports_status_check CHECK (((status)::text = ANY ((ARRAY['passed'::character varying, 'failed'::character varying, 'partial'::character varying, 'accepted'::character varying])::text[])))
);

--
-- Name: inspection_acceptance_reports_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.inspection_acceptance_reports_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: inspection_acceptance_reports_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.inspection_acceptance_reports_id_seq OWNED BY public.inspection_acceptance_reports.id;

--
-- Name: inventory_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inventory_logs (
    id bigint NOT NULL,
    item_id bigint,
    user_id bigint NOT NULL,
    action character varying(255) NOT NULL,
    quantity integer,
    previous_quantity integer,
    new_quantity integer,
    reference_type character varying(255),
    reference_id bigint,
    notes text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

--
-- Name: inventory_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.inventory_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: inventory_logs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.inventory_logs_id_seq OWNED BY public.inventory_logs.id;

--
-- Name: items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.items (
    id bigint NOT NULL,
    code character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    category_id bigint NOT NULL,
    unit_of_measure character varying(255) NOT NULL,
    weighted_average_cost numeric(15,2) DEFAULT '0'::numeric NOT NULL,
    current_stock integer DEFAULT 0 NOT NULL,
    min_stock integer DEFAULT 0 NOT NULL,
    max_stock integer,
    is_expandable boolean DEFAULT false NOT NULL,
    is_semi_expandable boolean DEFAULT false NOT NULL,
    qr_code_path character varying(255),
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    is_assembly boolean DEFAULT false NOT NULL
);

--
-- Name: items_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: items_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.items_id_seq OWNED BY public.items.id;

--
-- Name: login_attempts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.login_attempts (
    id bigint NOT NULL,
    email character varying(255) NOT NULL,
    ip_address character varying(45) NOT NULL,
    user_agent character varying(255),
    successful boolean DEFAULT false NOT NULL,
    failure_reason character varying(255),
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

--
-- Name: login_attempts_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.login_attempts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: login_attempts_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.login_attempts_id_seq OWNED BY public.login_attempts.id;

--
-- Name: migrations_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.migrations_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: notifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notifications (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    type character varying(255) NOT NULL,
    title character varying(255) NOT NULL,
    message text NOT NULL,
    related_type character varying(255),
    related_id bigint,
    is_read boolean DEFAULT false NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

--
-- Name: notifications_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.notifications_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: notifications_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.notifications_id_seq OWNED BY public.notifications.id;

--
-- Name: personal_access_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.personal_access_tokens (
    id bigint NOT NULL,
    tokenable_type character varying(255) NOT NULL,
    tokenable_id bigint NOT NULL,
    name character varying(255) NOT NULL,
    token character varying(64) NOT NULL,
    abilities text,
    last_used_at timestamp(0) without time zone,
    expires_at timestamp(0) without time zone,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

--
-- Name: personal_access_tokens_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.personal_access_tokens_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: personal_access_tokens_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.personal_access_tokens_id_seq OWNED BY public.personal_access_tokens.id;

--
-- Name: physical_inventory_count_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.physical_inventory_count_items (
    id bigint NOT NULL,
    physical_inventory_count_id bigint CONSTRAINT physical_inventory_count_it_physical_inventory_count_i_not_null NOT NULL,
    item_id bigint NOT NULL,
    system_quantity integer DEFAULT 0 NOT NULL,
    counted_quantity integer NOT NULL,
    location character varying(255),
    remarks text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

--
-- Name: physical_inventory_count_items_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.physical_inventory_count_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: physical_inventory_count_items_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.physical_inventory_count_items_id_seq OWNED BY public.physical_inventory_count_items.id;

--
-- Name: physical_inventory_counts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.physical_inventory_counts (
    id bigint NOT NULL,
    count_number character varying(255) NOT NULL,
    count_date date NOT NULL,
    initiated_by bigint NOT NULL,
    status character varying(255) DEFAULT 'draft'::character varying NOT NULL,
    purpose text,
    total_items_counted integer DEFAULT 0 NOT NULL,
    total_items_found integer DEFAULT 0 NOT NULL,
    total_discrepancies integer DEFAULT 0 NOT NULL,
    remarks text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    CONSTRAINT physical_inventory_counts_status_check CHECK (((status)::text = ANY ((ARRAY['draft'::character varying, 'in_progress'::character varying, 'completed'::character varying, 'verified'::character varying])::text[])))
);

--
-- Name: physical_inventory_counts_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.physical_inventory_counts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: physical_inventory_counts_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.physical_inventory_counts_id_seq OWNED BY public.physical_inventory_counts.id;

--
-- Name: ppmp_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ppmp_items (
    id bigint NOT NULL,
    ppmp_id bigint NOT NULL,
    general_description character varying(255) NOT NULL,
    code character varying(255),
    unit character varying(255),
    quantity integer DEFAULT 1 NOT NULL,
    estimated_unit_cost numeric(15,2) DEFAULT '0'::numeric NOT NULL,
    estimated_total numeric(15,2) DEFAULT '0'::numeric NOT NULL,
    procurement_mode character varying(255) DEFAULT 'Competitive Bidding'::character varying NOT NULL,
    schedule character varying(255),
    fund_source character varying(255),
    remarks text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    is_sustainable boolean DEFAULT false NOT NULL,
    specifications text,
    procurement_category character varying(100) DEFAULT 'Goods'::character varying NOT NULL
);

--
-- Name: ppmp_items_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ppmp_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: ppmp_items_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ppmp_items_id_seq OWNED BY public.ppmp_items.id;

--
-- Name: ppmps; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ppmps (
    id bigint NOT NULL,
    ppmp_number character varying(255) NOT NULL,
    department_id bigint NOT NULL,
    fiscal_year smallint NOT NULL,
    title character varying(255) NOT NULL,
    status character varying(255) DEFAULT 'draft'::character varying NOT NULL,
    total_estimated_budget numeric(15,2) DEFAULT '0'::numeric NOT NULL,
    revision integer DEFAULT 0 NOT NULL,
    prepared_by bigint,
    reviewed_by bigint,
    approved_by bigint,
    submitted_at timestamp(0) without time zone,
    approved_at timestamp(0) without time zone,
    return_reason text,
    remarks text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    deleted_at timestamp(0) without time zone
);

--
-- Name: ppmps_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ppmps_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: ppmps_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ppmps_id_seq OWNED BY public.ppmps.id;

--
-- Name: procurement_milestones; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.procurement_milestones (
    id bigint NOT NULL,
    milestoneable_type character varying(255) NOT NULL,
    milestoneable_id bigint NOT NULL,
    sequence smallint DEFAULT '0'::smallint NOT NULL,
    stage character varying(255) NOT NULL,
    target_date date,
    actual_date date,
    remarks text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

--
-- Name: procurement_milestones_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.procurement_milestones_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: procurement_milestones_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.procurement_milestones_id_seq OWNED BY public.procurement_milestones.id;

--
-- Name: properties; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.properties (
    id bigint NOT NULL,
    item_id bigint NOT NULL,
    serial_number character varying(255),
    tag_number character varying(255) NOT NULL,
    assigned_to bigint NOT NULL,
    assigned_date date NOT NULL,
    status character varying(255) DEFAULT 'assigned'::character varying NOT NULL,
    condition character varying(255) DEFAULT 'good'::character varying NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    qr_code_path character varying(255),
    acquisition_cost numeric(15,2),
    salvage_value numeric(15,2),
    acquisition_date date,
    useful_life_years integer DEFAULT 5 NOT NULL,
    depreciation_method character varying(255) DEFAULT 'straight-line'::character varying NOT NULL,
    depreciation_rate numeric(5,2),
    accumulated_depreciation numeric(15,2) DEFAULT '0'::numeric NOT NULL,
    depreciation_start_date date,
    last_depreciation_calculation timestamp(0) without time zone,
    is_depreciable boolean DEFAULT true NOT NULL,
    current_book_value numeric(15,2),
    parent_id bigint,
    stock_id bigint,
    brand character varying(255),
    specifications text,
    warranty character varying(255),
    warranty_months integer,
    CONSTRAINT properties_condition_check CHECK (((condition)::text = ANY ((ARRAY['excellent'::character varying, 'good'::character varying, 'fair'::character varying, 'poor'::character varying])::text[]))),
    CONSTRAINT properties_depreciation_method_check CHECK (((depreciation_method)::text = ANY ((ARRAY['straight-line'::character varying, 'declining-balance'::character varying])::text[]))),
    CONSTRAINT properties_status_check CHECK (((status)::text = ANY ((ARRAY['assigned'::character varying, 'available'::character varying, 'under_repair'::character varying, 'disposed'::character varying])::text[])))
);

--
-- Name: properties_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.properties_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: properties_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.properties_id_seq OWNED BY public.properties.id;

--
-- Name: property_components; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.property_components (
    id bigint NOT NULL,
    property_id bigint NOT NULL,
    description character varying(255) NOT NULL,
    specifications character varying(255),
    serial_number character varying(255),
    quantity integer DEFAULT 1 NOT NULL,
    remarks text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    stock_id bigint,
    warranty character varying(255),
    warranty_months integer,
    warranty_start date
);

--
-- Name: property_components_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.property_components_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: property_components_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.property_components_id_seq OWNED BY public.property_components.id;

--
-- Name: property_depreciation_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.property_depreciation_logs (
    id bigint NOT NULL,
    property_id bigint NOT NULL,
    depreciation_amount numeric(15,2) NOT NULL,
    accumulated_depreciation numeric(15,2) NOT NULL,
    book_value numeric(15,2) NOT NULL,
    depreciation_date date NOT NULL,
    depreciation_period character varying(255) DEFAULT 'monthly'::character varying NOT NULL,
    calculation_method character varying(255) DEFAULT 'straight-line'::character varying NOT NULL,
    notes text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    CONSTRAINT property_depreciation_logs_calculation_method_check CHECK (((calculation_method)::text = ANY ((ARRAY['straight-line'::character varying, 'declining-balance'::character varying])::text[]))),
    CONSTRAINT property_depreciation_logs_depreciation_period_check CHECK (((depreciation_period)::text = ANY ((ARRAY['monthly'::character varying, 'quarterly'::character varying, 'annually'::character varying])::text[])))
);

--
-- Name: property_depreciation_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.property_depreciation_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: property_depreciation_logs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.property_depreciation_logs_id_seq OWNED BY public.property_depreciation_logs.id;

--
-- Name: property_transfers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.property_transfers (
    id bigint NOT NULL,
    property_id bigint NOT NULL,
    from_user bigint NOT NULL,
    to_user bigint NOT NULL,
    transfer_date date NOT NULL,
    transfer_reason text NOT NULL,
    approved_by bigint,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    reference_number character varying(255)
);

--
-- Name: property_transfers_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.property_transfers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: property_transfers_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.property_transfers_id_seq OWNED BY public.property_transfers.id;

--
-- Name: purchase_order_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.purchase_order_items (
    id bigint NOT NULL,
    purchase_order_id bigint NOT NULL,
    description character varying(255) NOT NULL,
    quantity integer DEFAULT 1 NOT NULL,
    unit_cost numeric(12,2) DEFAULT '0'::numeric NOT NULL,
    total_cost numeric(12,2) DEFAULT '0'::numeric NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    specifications text
);

--
-- Name: purchase_order_items_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.purchase_order_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: purchase_order_items_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.purchase_order_items_id_seq OWNED BY public.purchase_order_items.id;

--
-- Name: purchase_orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.purchase_orders (
    id bigint NOT NULL,
    po_number character varying(255) NOT NULL,
    purchase_request_id bigint,
    supplier_id bigint NOT NULL,
    order_date date NOT NULL,
    delivery_date date,
    status character varying(255) DEFAULT 'draft'::character varying NOT NULL,
    total_amount numeric(12,2) NOT NULL,
    delivery_address text,
    remarks text,
    approved_by bigint,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    deleted_at timestamp(0) without time zone,
    rfq_id bigint,
    bidding_id bigint,
    CONSTRAINT purchase_orders_status_check CHECK (((status)::text = ANY ((ARRAY['draft'::character varying, 'sent'::character varying, 'confirmed'::character varying, 'partially_received'::character varying, 'received'::character varying, 'cancelled'::character varying])::text[])))
);

--
-- Name: purchase_orders_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.purchase_orders_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: purchase_orders_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.purchase_orders_id_seq OWNED BY public.purchase_orders.id;

--
-- Name: purchase_request_approvals; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.purchase_request_approvals (
    id bigint NOT NULL,
    purchase_request_id bigint NOT NULL,
    step integer NOT NULL,
    role_key character varying(255) NOT NULL,
    label character varying(255) NOT NULL,
    required_permission character varying(255) NOT NULL,
    status character varying(255) DEFAULT 'pending'::character varying NOT NULL,
    acted_by bigint,
    acted_at timestamp(0) without time zone,
    remarks text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

--
-- Name: purchase_request_approvals_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.purchase_request_approvals_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: purchase_request_approvals_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.purchase_request_approvals_id_seq OWNED BY public.purchase_request_approvals.id;

--
-- Name: purchase_request_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.purchase_request_items (
    id bigint NOT NULL,
    purchase_request_id bigint NOT NULL,
    app_item_id bigint,
    general_description character varying(255) NOT NULL,
    unit character varying(255),
    quantity integer DEFAULT 1 NOT NULL,
    estimated_unit_cost numeric(15,2) DEFAULT '0'::numeric NOT NULL,
    estimated_total numeric(15,2) DEFAULT '0'::numeric NOT NULL,
    specifications text,
    remarks text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    is_sustainable boolean DEFAULT false NOT NULL,
    fund_source character varying(255),
    procurement_mode character varying(100),
    procurement_category character varying(100) DEFAULT 'Goods'::character varying NOT NULL
);

--
-- Name: purchase_request_items_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.purchase_request_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: purchase_request_items_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.purchase_request_items_id_seq OWNED BY public.purchase_request_items.id;

--
-- Name: purchase_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.purchase_requests (
    id bigint NOT NULL,
    pr_number character varying(255) NOT NULL,
    department_id bigint NOT NULL,
    requested_by bigint NOT NULL,
    request_date date NOT NULL,
    purpose text,
    status character varying(30) DEFAULT 'draft'::character varying NOT NULL,
    priority character varying(255) DEFAULT 'medium'::character varying NOT NULL,
    total_amount numeric(12,2),
    remarks text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    deleted_at timestamp(0) without time zone,
    current_step integer,
    app_id bigint,
    fiscal_year smallint,
    return_reason text,
    cancelled_at timestamp(0) without time zone,
    caf_number character varying(255),
    caf_certified_by bigint,
    caf_certified_at timestamp(0) without time zone,
    caf_amount numeric(15,2),
    caf_remarks text,
    CONSTRAINT purchase_requests_priority_check CHECK (((priority)::text = ANY ((ARRAY['low'::character varying, 'medium'::character varying, 'high'::character varying])::text[])))
);

--
-- Name: purchase_requests_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.purchase_requests_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: purchase_requests_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.purchase_requests_id_seq OWNED BY public.purchase_requests.id;

--
-- Name: request_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.request_items (
    id bigint NOT NULL,
    request_id bigint NOT NULL,
    item_id bigint NOT NULL,
    quantity integer NOT NULL,
    approved_quantity integer DEFAULT 0 NOT NULL,
    status character varying(255) DEFAULT 'pending'::character varying NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    CONSTRAINT request_items_status_check CHECK (((status)::text = ANY ((ARRAY['pending'::character varying, 'approved'::character varying, 'rejected'::character varying, 'fulfilled'::character varying])::text[])))
);

--
-- Name: request_items_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.request_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: request_items_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.request_items_id_seq OWNED BY public.request_items.id;

--
-- Name: request_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.request_logs (
    id bigint NOT NULL,
    department_id bigint NOT NULL,
    item_id bigint NOT NULL,
    quantity integer NOT NULL,
    requested_by bigint NOT NULL,
    requested_at timestamp(0) without time zone NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

--
-- Name: request_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.request_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: request_logs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.request_logs_id_seq OWNED BY public.request_logs.id;

--
-- Name: requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.requests (
    id bigint NOT NULL,
    request_number character varying(255) NOT NULL,
    department_id bigint,
    requested_by bigint,
    request_date date,
    status character varying(255) DEFAULT 'pending'::character varying NOT NULL,
    priority character varying(255) DEFAULT 'medium'::character varying NOT NULL,
    purpose text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    ris_number character varying(255),
    ris_date date,
    requisitioner character varying(255),
    par_number character varying(255),
    par_date date,
    employee_name character varying(255),
    "position" character varying(255),
    property_number character varying(255),
    description text,
    ics_number character varying(255),
    ics_date date,
    custodian_name character varying(255),
    office_department character varying(255),
    item_description character varying(255),
    quantity numeric(10,2),
    unit_of_measure character varying(255),
    remarks text,
    CONSTRAINT requests_priority_check CHECK (((priority)::text = ANY ((ARRAY['low'::character varying, 'medium'::character varying, 'high'::character varying])::text[]))),
    CONSTRAINT requests_status_check CHECK (((status)::text = ANY ((ARRAY['pending'::character varying, 'approved'::character varying, 'rejected'::character varying, 'partially_fulfilled'::character varying, 'fulfilled'::character varying])::text[])))
);

--
-- Name: requests_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.requests_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: requests_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.requests_id_seq OWNED BY public.requests.id;

--
-- Name: rfq_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.rfq_items (
    id bigint NOT NULL,
    rfq_id bigint NOT NULL,
    general_description character varying(255) NOT NULL,
    unit character varying(255),
    quantity integer DEFAULT 1 NOT NULL,
    abc_unit_cost numeric(15,2) DEFAULT '0'::numeric NOT NULL,
    remarks text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    delivery_lead_time character varying(255),
    purchase_request_item_id bigint,
    specifications text
);

--
-- Name: rfq_items_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.rfq_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: rfq_items_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.rfq_items_id_seq OWNED BY public.rfq_items.id;

--
-- Name: rfq_quotation_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.rfq_quotation_items (
    id bigint NOT NULL,
    rfq_quotation_id bigint NOT NULL,
    rfq_item_id bigint NOT NULL,
    unit_price numeric(15,2) DEFAULT '0'::numeric NOT NULL,
    total_price numeric(15,2) DEFAULT '0'::numeric NOT NULL,
    remarks text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

--
-- Name: rfq_quotation_items_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.rfq_quotation_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: rfq_quotation_items_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.rfq_quotation_items_id_seq OWNED BY public.rfq_quotation_items.id;

--
-- Name: rfq_quotations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.rfq_quotations (
    id bigint NOT NULL,
    rfq_id bigint NOT NULL,
    supplier_id bigint NOT NULL,
    quotation_number character varying(255),
    total_amount numeric(15,2) DEFAULT '0'::numeric NOT NULL,
    submitted_at date,
    is_winner boolean DEFAULT false NOT NULL,
    remarks text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    is_responsive boolean DEFAULT true NOT NULL
);

--
-- Name: rfq_quotations_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.rfq_quotations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: rfq_quotations_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.rfq_quotations_id_seq OWNED BY public.rfq_quotations.id;

--
-- Name: rfqs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.rfqs (
    id bigint NOT NULL,
    rfq_number character varying(255) NOT NULL,
    title character varying(255) NOT NULL,
    purchase_request_id bigint,
    procurement_method character varying(255) NOT NULL,
    status character varying(255) DEFAULT 'draft'::character varying NOT NULL,
    abc numeric(15,2) DEFAULT '0'::numeric NOT NULL,
    deadline timestamp(0) without time zone,
    description text,
    prepared_by bigint,
    awarded_supplier_id bigint,
    awarded_at timestamp(0) without time zone,
    remarks text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    deleted_at timestamp(0) without time zone,
    noa_number character varying(255),
    noa_date date,
    ntp_number character varying(255),
    ntp_date date,
    delivery_term character varying(255),
    delivery_lead_days integer,
    justification text
);

--
-- Name: rfqs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.rfqs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: rfqs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.rfqs_id_seq OWNED BY public.rfqs.id;

--
-- Name: roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.roles (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    permissions json NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    description character varying(255),
    is_system boolean DEFAULT false NOT NULL
);

--
-- Name: roles_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.roles_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: roles_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.roles_id_seq OWNED BY public.roles.id;

--
-- Name: rsmi_reports; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.rsmi_reports (
    id bigint NOT NULL,
    report_number character varying(255) NOT NULL,
    year integer NOT NULL,
    month integer NOT NULL,
    data json NOT NULL,
    generated_at timestamp(0) without time zone NOT NULL,
    generated_by bigint,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

--
-- Name: rsmi_reports_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.rsmi_reports_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: rsmi_reports_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.rsmi_reports_id_seq OWNED BY public.rsmi_reports.id;

--
-- Name: semi_expendable_issues; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.semi_expendable_issues (
    id bigint NOT NULL,
    item_id bigint NOT NULL,
    request_id bigint,
    issued_to bigint,
    department_id bigint,
    custodian_name character varying(255),
    ics_number character varying(255),
    quantity integer DEFAULT 0 NOT NULL,
    quantity_returned integer DEFAULT 0 NOT NULL,
    issued_date date NOT NULL,
    status character varying(255) DEFAULT 'issued'::character varying NOT NULL,
    remarks text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

--
-- Name: semi_expendable_issues_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.semi_expendable_issues_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: semi_expendable_issues_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.semi_expendable_issues_id_seq OWNED BY public.semi_expendable_issues.id;

--
-- Name: settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.settings (
    id bigint NOT NULL,
    key character varying(255) NOT NULL,
    value text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

--
-- Name: settings_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.settings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: settings_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.settings_id_seq OWNED BY public.settings.id;

--
-- Name: stock_adjustments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stock_adjustments (
    id bigint NOT NULL,
    adjustment_number character varying(255) NOT NULL,
    item_id bigint NOT NULL,
    adjustment_type character varying(10) NOT NULL,
    quantity integer NOT NULL,
    reason character varying(255) NOT NULL,
    status character varying(20) DEFAULT 'pending'::character varying NOT NULL,
    previous_quantity integer,
    new_quantity integer,
    requested_by bigint,
    approved_by bigint,
    approved_at timestamp(0) without time zone,
    remarks text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

--
-- Name: stock_adjustments_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.stock_adjustments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: stock_adjustments_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.stock_adjustments_id_seq OWNED BY public.stock_adjustments.id;

--
-- Name: stock_transfers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stock_transfers (
    id bigint NOT NULL,
    transfer_number character varying(255) NOT NULL,
    item_id bigint NOT NULL,
    stock_id bigint,
    from_location character varying(255),
    to_location character varying(255) NOT NULL,
    quantity integer NOT NULL,
    transferred_by bigint,
    transfer_date date NOT NULL,
    remarks text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

--
-- Name: stock_transfers_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.stock_transfers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: stock_transfers_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.stock_transfers_id_seq OWNED BY public.stock_transfers.id;

--
-- Name: stocks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stocks (
    id bigint NOT NULL,
    item_id bigint NOT NULL,
    quantity integer NOT NULL,
    unit_cost numeric(15,2) NOT NULL,
    lot_number character varying(255),
    expiration_date date,
    received_date date NOT NULL,
    supplier_id bigint,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    iar_id bigint,
    brand_name character varying(255),
    location character varying(255),
    specifications text,
    warranty character varying(255),
    warranty_months integer
);

--
-- Name: stocks_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.stocks_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: stocks_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.stocks_id_seq OWNED BY public.stocks.id;

--
-- Name: supplier_blacklists; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.supplier_blacklists (
    id bigint NOT NULL,
    supplier_id bigint NOT NULL,
    sanction_type character varying(255) DEFAULT 'Blacklist'::character varying NOT NULL,
    grounds text NOT NULL,
    reference_number character varying(255),
    start_date date NOT NULL,
    end_date date,
    status character varying(255) DEFAULT 'active'::character varying NOT NULL,
    lifted_at date,
    lifted_reason text,
    created_by bigint,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

--
-- Name: supplier_blacklists_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.supplier_blacklists_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: supplier_blacklists_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.supplier_blacklists_id_seq OWNED BY public.supplier_blacklists.id;

--
-- Name: supplier_compliance_documents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.supplier_compliance_documents (
    id bigint NOT NULL,
    supplier_id bigint NOT NULL,
    document_type character varying(255) NOT NULL,
    reference_number character varying(255),
    issued_date date,
    expiry_date date,
    remarks text,
    document_id bigint,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

--
-- Name: supplier_compliance_documents_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.supplier_compliance_documents_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: supplier_compliance_documents_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.supplier_compliance_documents_id_seq OWNED BY public.supplier_compliance_documents.id;

--
-- Name: supplier_evaluations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.supplier_evaluations (
    id bigint NOT NULL,
    supplier_id bigint NOT NULL,
    evaluation_date date NOT NULL,
    delivery_rating smallint NOT NULL,
    quality_rating smallint NOT NULL,
    overall_rating numeric(3,2) DEFAULT '0'::numeric NOT NULL,
    reference character varying(255),
    remarks text,
    evaluated_by bigint,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    timeliness_rating smallint,
    compliance_rating smallint,
    after_sales_rating smallint
);

--
-- Name: supplier_evaluations_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.supplier_evaluations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: supplier_evaluations_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.supplier_evaluations_id_seq OWNED BY public.supplier_evaluations.id;

--
-- Name: suppliers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.suppliers (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    contact_person character varying(255),
    email character varying(255),
    phone character varying(255),
    address text,
    website character varying(255),
    notes text,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    vendor_code character varying(255),
    mobile_number character varying(255),
    credit_terms character varying(255),
    tin character varying(30),
    philgeps_reg_number character varying(255),
    is_eligible boolean DEFAULT true NOT NULL,
    is_msme boolean DEFAULT false NOT NULL
);

--
-- Name: suppliers_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.suppliers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: suppliers_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.suppliers_id_seq OWNED BY public.suppliers.id;

--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    email character varying(255) NOT NULL,
    email_verified_at timestamp(0) without time zone,
    password character varying(255) NOT NULL,
    role_id bigint NOT NULL,
    department_id bigint NOT NULL,
    remember_token character varying(100),
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    two_factor_secret character varying(255),
    two_factor_enabled boolean DEFAULT false NOT NULL,
    password_changed_at timestamp(0) without time zone,
    signature_path character varying(255)
);

--
-- Name: users_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

--
-- Name: users_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.users_id_seq OWNED BY public.users.id;

--
-- Name: apcpi_assessments id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.apcpi_assessments ALTER COLUMN id SET DEFAULT nextval('public.apcpi_assessments_id_seq'::regclass);

--
-- Name: apcpi_scores id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.apcpi_scores ALTER COLUMN id SET DEFAULT nextval('public.apcpi_scores_id_seq'::regclass);

--
-- Name: app_items id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_items ALTER COLUMN id SET DEFAULT nextval('public.app_items_id_seq'::regclass);

--
-- Name: apps id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.apps ALTER COLUMN id SET DEFAULT nextval('public.apps_id_seq'::regclass);

--
-- Name: audit_trails id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_trails ALTER COLUMN id SET DEFAULT nextval('public.audit_trails_id_seq'::regclass);

--
-- Name: bac_evaluations id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bac_evaluations ALTER COLUMN id SET DEFAULT nextval('public.bac_evaluations_id_seq'::regclass);

--
-- Name: bac_meeting_attendees id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bac_meeting_attendees ALTER COLUMN id SET DEFAULT nextval('public.bac_meeting_attendees_id_seq'::regclass);

--
-- Name: bac_meetings id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bac_meetings ALTER COLUMN id SET DEFAULT nextval('public.bac_meetings_id_seq'::regclass);

--
-- Name: bac_members id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bac_members ALTER COLUMN id SET DEFAULT nextval('public.bac_members_id_seq'::regclass);

--
-- Name: bac_resolutions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bac_resolutions ALTER COLUMN id SET DEFAULT nextval('public.bac_resolutions_id_seq'::regclass);

--
-- Name: bid_observers id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bid_observers ALTER COLUMN id SET DEFAULT nextval('public.bid_observers_id_seq'::regclass);

--
-- Name: bid_protests id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bid_protests ALTER COLUMN id SET DEFAULT nextval('public.bid_protests_id_seq'::regclass);

--
-- Name: bidding_bidder_scores id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bidding_bidder_scores ALTER COLUMN id SET DEFAULT nextval('public.bidding_bidder_scores_id_seq'::regclass);

--
-- Name: bidding_bidders id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bidding_bidders ALTER COLUMN id SET DEFAULT nextval('public.bidding_bidders_id_seq'::regclass);

--
-- Name: bidding_criteria id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bidding_criteria ALTER COLUMN id SET DEFAULT nextval('public.bidding_criteria_id_seq'::regclass);

--
-- Name: bidding_items id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bidding_items ALTER COLUMN id SET DEFAULT nextval('public.bidding_items_id_seq'::regclass);

--
-- Name: biddings id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.biddings ALTER COLUMN id SET DEFAULT nextval('public.biddings_id_seq'::regclass);

--
-- Name: categories id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories ALTER COLUMN id SET DEFAULT nextval('public.categories_id_seq'::regclass);

--
-- Name: contract_amendments id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contract_amendments ALTER COLUMN id SET DEFAULT nextval('public.contract_amendments_id_seq'::regclass);

--
-- Name: contract_securities id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contract_securities ALTER COLUMN id SET DEFAULT nextval('public.contract_securities_id_seq'::regclass);

--
-- Name: contracts id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contracts ALTER COLUMN id SET DEFAULT nextval('public.contracts_id_seq'::regclass);

--
-- Name: delivery_extensions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_extensions ALTER COLUMN id SET DEFAULT nextval('public.delivery_extensions_id_seq'::regclass);

--
-- Name: departments id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.departments ALTER COLUMN id SET DEFAULT nextval('public.departments_id_seq'::regclass);

--
-- Name: disposal_notifications id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.disposal_notifications ALTER COLUMN id SET DEFAULT nextval('public.disposal_notifications_id_seq'::regclass);

--
-- Name: disposal_requests id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.disposal_requests ALTER COLUMN id SET DEFAULT nextval('public.disposal_requests_id_seq'::regclass);

--
-- Name: document_logos id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document_logos ALTER COLUMN id SET DEFAULT nextval('public.document_logos_id_seq'::regclass);

--
-- Name: document_versions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document_versions ALTER COLUMN id SET DEFAULT nextval('public.document_versions_id_seq'::regclass);

--
-- Name: documents id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documents ALTER COLUMN id SET DEFAULT nextval('public.documents_id_seq'::regclass);

--
-- Name: electronic_signatures id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.electronic_signatures ALTER COLUMN id SET DEFAULT nextval('public.electronic_signatures_id_seq'::regclass);

--
-- Name: framework_calloffs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.framework_calloffs ALTER COLUMN id SET DEFAULT nextval('public.framework_calloffs_id_seq'::regclass);

--
-- Name: gate_passes id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gate_passes ALTER COLUMN id SET DEFAULT nextval('public.gate_passes_id_seq'::regclass);

--
-- Name: iar_items id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iar_items ALTER COLUMN id SET DEFAULT nextval('public.iar_items_id_seq'::regclass);

--
-- Name: inspection_acceptance_reports id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inspection_acceptance_reports ALTER COLUMN id SET DEFAULT nextval('public.inspection_acceptance_reports_id_seq'::regclass);

--
-- Name: inventory_logs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_logs ALTER COLUMN id SET DEFAULT nextval('public.inventory_logs_id_seq'::regclass);

--
-- Name: items id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.items ALTER COLUMN id SET DEFAULT nextval('public.items_id_seq'::regclass);

--
-- Name: login_attempts id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.login_attempts ALTER COLUMN id SET DEFAULT nextval('public.login_attempts_id_seq'::regclass);

--
-- Name: notifications id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications ALTER COLUMN id SET DEFAULT nextval('public.notifications_id_seq'::regclass);

--
-- Name: personal_access_tokens id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.personal_access_tokens ALTER COLUMN id SET DEFAULT nextval('public.personal_access_tokens_id_seq'::regclass);

--
-- Name: physical_inventory_count_items id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.physical_inventory_count_items ALTER COLUMN id SET DEFAULT nextval('public.physical_inventory_count_items_id_seq'::regclass);

--
-- Name: physical_inventory_counts id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.physical_inventory_counts ALTER COLUMN id SET DEFAULT nextval('public.physical_inventory_counts_id_seq'::regclass);

--
-- Name: ppmp_items id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ppmp_items ALTER COLUMN id SET DEFAULT nextval('public.ppmp_items_id_seq'::regclass);

--
-- Name: ppmps id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ppmps ALTER COLUMN id SET DEFAULT nextval('public.ppmps_id_seq'::regclass);

--
-- Name: procurement_milestones id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.procurement_milestones ALTER COLUMN id SET DEFAULT nextval('public.procurement_milestones_id_seq'::regclass);

--
-- Name: properties id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.properties ALTER COLUMN id SET DEFAULT nextval('public.properties_id_seq'::regclass);

--
-- Name: property_components id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_components ALTER COLUMN id SET DEFAULT nextval('public.property_components_id_seq'::regclass);

--
-- Name: property_depreciation_logs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_depreciation_logs ALTER COLUMN id SET DEFAULT nextval('public.property_depreciation_logs_id_seq'::regclass);

--
-- Name: property_transfers id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_transfers ALTER COLUMN id SET DEFAULT nextval('public.property_transfers_id_seq'::regclass);

--
-- Name: purchase_order_items id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_order_items ALTER COLUMN id SET DEFAULT nextval('public.purchase_order_items_id_seq'::regclass);

--
-- Name: purchase_orders id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_orders ALTER COLUMN id SET DEFAULT nextval('public.purchase_orders_id_seq'::regclass);

--
-- Name: purchase_request_approvals id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_request_approvals ALTER COLUMN id SET DEFAULT nextval('public.purchase_request_approvals_id_seq'::regclass);

--
-- Name: purchase_request_items id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_request_items ALTER COLUMN id SET DEFAULT nextval('public.purchase_request_items_id_seq'::regclass);

--
-- Name: purchase_requests id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_requests ALTER COLUMN id SET DEFAULT nextval('public.purchase_requests_id_seq'::regclass);

--
-- Name: request_items id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.request_items ALTER COLUMN id SET DEFAULT nextval('public.request_items_id_seq'::regclass);

--
-- Name: request_logs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.request_logs ALTER COLUMN id SET DEFAULT nextval('public.request_logs_id_seq'::regclass);

--
-- Name: requests id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.requests ALTER COLUMN id SET DEFAULT nextval('public.requests_id_seq'::regclass);

--
-- Name: rfq_items id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rfq_items ALTER COLUMN id SET DEFAULT nextval('public.rfq_items_id_seq'::regclass);

--
-- Name: rfq_quotation_items id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rfq_quotation_items ALTER COLUMN id SET DEFAULT nextval('public.rfq_quotation_items_id_seq'::regclass);

--
-- Name: rfq_quotations id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rfq_quotations ALTER COLUMN id SET DEFAULT nextval('public.rfq_quotations_id_seq'::regclass);

--
-- Name: rfqs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rfqs ALTER COLUMN id SET DEFAULT nextval('public.rfqs_id_seq'::regclass);

--
-- Name: roles id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles ALTER COLUMN id SET DEFAULT nextval('public.roles_id_seq'::regclass);

--
-- Name: rsmi_reports id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rsmi_reports ALTER COLUMN id SET DEFAULT nextval('public.rsmi_reports_id_seq'::regclass);

--
-- Name: semi_expendable_issues id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.semi_expendable_issues ALTER COLUMN id SET DEFAULT nextval('public.semi_expendable_issues_id_seq'::regclass);

--
-- Name: settings id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.settings ALTER COLUMN id SET DEFAULT nextval('public.settings_id_seq'::regclass);

--
-- Name: stock_adjustments id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_adjustments ALTER COLUMN id SET DEFAULT nextval('public.stock_adjustments_id_seq'::regclass);

--
-- Name: stock_transfers id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_transfers ALTER COLUMN id SET DEFAULT nextval('public.stock_transfers_id_seq'::regclass);

--
-- Name: stocks id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stocks ALTER COLUMN id SET DEFAULT nextval('public.stocks_id_seq'::regclass);

--
-- Name: supplier_blacklists id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplier_blacklists ALTER COLUMN id SET DEFAULT nextval('public.supplier_blacklists_id_seq'::regclass);

--
-- Name: supplier_compliance_documents id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplier_compliance_documents ALTER COLUMN id SET DEFAULT nextval('public.supplier_compliance_documents_id_seq'::regclass);

--
-- Name: supplier_evaluations id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplier_evaluations ALTER COLUMN id SET DEFAULT nextval('public.supplier_evaluations_id_seq'::regclass);

--
-- Name: suppliers id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.suppliers ALTER COLUMN id SET DEFAULT nextval('public.suppliers_id_seq'::regclass);

--
-- Name: users id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users ALTER COLUMN id SET DEFAULT nextval('public.users_id_seq'::regclass);

--
-- Name: apcpi_assessments apcpi_assessments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.apcpi_assessments
    ADD CONSTRAINT apcpi_assessments_pkey PRIMARY KEY (id);

--
-- Name: apcpi_scores apcpi_score_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.apcpi_scores
    ADD CONSTRAINT apcpi_score_unique UNIQUE (apcpi_assessment_id, indicator_key);

--
-- Name: apcpi_scores apcpi_scores_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.apcpi_scores
    ADD CONSTRAINT apcpi_scores_pkey PRIMARY KEY (id);

--
-- Name: app_items app_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_items
    ADD CONSTRAINT app_items_pkey PRIMARY KEY (id);

--
-- Name: apps apps_app_number_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.apps
    ADD CONSTRAINT apps_app_number_unique UNIQUE (app_number);

--
-- Name: apps apps_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.apps
    ADD CONSTRAINT apps_pkey PRIMARY KEY (id);

--
-- Name: audit_trails audit_trails_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_trails
    ADD CONSTRAINT audit_trails_pkey PRIMARY KEY (id);

--
-- Name: bac_evaluations bac_evaluations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bac_evaluations
    ADD CONSTRAINT bac_evaluations_pkey PRIMARY KEY (id);

--
-- Name: bac_meeting_attendees bac_meeting_attendees_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bac_meeting_attendees
    ADD CONSTRAINT bac_meeting_attendees_pkey PRIMARY KEY (id);

--
-- Name: bac_meetings bac_meetings_meeting_number_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bac_meetings
    ADD CONSTRAINT bac_meetings_meeting_number_unique UNIQUE (meeting_number);

--
-- Name: bac_meetings bac_meetings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bac_meetings
    ADD CONSTRAINT bac_meetings_pkey PRIMARY KEY (id);

--
-- Name: bac_members bac_members_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bac_members
    ADD CONSTRAINT bac_members_pkey PRIMARY KEY (id);

--
-- Name: bac_resolutions bac_resolutions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bac_resolutions
    ADD CONSTRAINT bac_resolutions_pkey PRIMARY KEY (id);

--
-- Name: bac_resolutions bac_resolutions_resolution_number_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bac_resolutions
    ADD CONSTRAINT bac_resolutions_resolution_number_unique UNIQUE (resolution_number);

--
-- Name: bidding_bidder_scores bbs_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bidding_bidder_scores
    ADD CONSTRAINT bbs_unique UNIQUE (bidding_bidder_id, bidding_criterion_id);

--
-- Name: bid_observers bid_observers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bid_observers
    ADD CONSTRAINT bid_observers_pkey PRIMARY KEY (id);

--
-- Name: bid_protests bid_protests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bid_protests
    ADD CONSTRAINT bid_protests_pkey PRIMARY KEY (id);

--
-- Name: bidding_bidder_scores bidding_bidder_scores_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bidding_bidder_scores
    ADD CONSTRAINT bidding_bidder_scores_pkey PRIMARY KEY (id);

--
-- Name: bidding_bidders bidding_bidders_bidding_id_supplier_id_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bidding_bidders
    ADD CONSTRAINT bidding_bidders_bidding_id_supplier_id_unique UNIQUE (bidding_id, supplier_id);

--
-- Name: bidding_bidders bidding_bidders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bidding_bidders
    ADD CONSTRAINT bidding_bidders_pkey PRIMARY KEY (id);

--
-- Name: bidding_criteria bidding_criteria_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bidding_criteria
    ADD CONSTRAINT bidding_criteria_pkey PRIMARY KEY (id);

--
-- Name: bidding_items bidding_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bidding_items
    ADD CONSTRAINT bidding_items_pkey PRIMARY KEY (id);

--
-- Name: biddings biddings_bidding_number_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.biddings
    ADD CONSTRAINT biddings_bidding_number_unique UNIQUE (bidding_number);

--
-- Name: biddings biddings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.biddings
    ADD CONSTRAINT biddings_pkey PRIMARY KEY (id);

--
-- Name: categories categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_pkey PRIMARY KEY (id);

--
-- Name: contract_amendments contract_amendments_contract_id_amendment_number_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contract_amendments
    ADD CONSTRAINT contract_amendments_contract_id_amendment_number_unique UNIQUE (contract_id, amendment_number);

--
-- Name: contract_amendments contract_amendments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contract_amendments
    ADD CONSTRAINT contract_amendments_pkey PRIMARY KEY (id);

--
-- Name: contract_securities contract_securities_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contract_securities
    ADD CONSTRAINT contract_securities_pkey PRIMARY KEY (id);

--
-- Name: contracts contracts_contract_number_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contracts
    ADD CONSTRAINT contracts_contract_number_unique UNIQUE (contract_number);

--
-- Name: contracts contracts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contracts
    ADD CONSTRAINT contracts_pkey PRIMARY KEY (id);

--
-- Name: delivery_extensions delivery_extensions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_extensions
    ADD CONSTRAINT delivery_extensions_pkey PRIMARY KEY (id);

--
-- Name: departments departments_code_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.departments
    ADD CONSTRAINT departments_code_unique UNIQUE (code);

--
-- Name: departments departments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.departments
    ADD CONSTRAINT departments_pkey PRIMARY KEY (id);

--
-- Name: disposal_notifications disposal_notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.disposal_notifications
    ADD CONSTRAINT disposal_notifications_pkey PRIMARY KEY (id);

--
-- Name: disposal_requests disposal_requests_disposal_number_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.disposal_requests
    ADD CONSTRAINT disposal_requests_disposal_number_unique UNIQUE (disposal_number);

--
-- Name: disposal_requests disposal_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.disposal_requests
    ADD CONSTRAINT disposal_requests_pkey PRIMARY KEY (id);

--
-- Name: document_logos document_logos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document_logos
    ADD CONSTRAINT document_logos_pkey PRIMARY KEY (id);

--
-- Name: document_versions document_versions_document_id_version_number_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document_versions
    ADD CONSTRAINT document_versions_document_id_version_number_unique UNIQUE (document_id, version_number);

--
-- Name: document_versions document_versions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document_versions
    ADD CONSTRAINT document_versions_pkey PRIMARY KEY (id);

--
-- Name: documents documents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documents
    ADD CONSTRAINT documents_pkey PRIMARY KEY (id);

--
-- Name: electronic_signatures electronic_signatures_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.electronic_signatures
    ADD CONSTRAINT electronic_signatures_pkey PRIMARY KEY (id);

--
-- Name: framework_calloffs framework_calloffs_calloff_number_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.framework_calloffs
    ADD CONSTRAINT framework_calloffs_calloff_number_unique UNIQUE (calloff_number);

--
-- Name: framework_calloffs framework_calloffs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.framework_calloffs
    ADD CONSTRAINT framework_calloffs_pkey PRIMARY KEY (id);

--
-- Name: gate_passes gate_passes_gate_pass_number_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gate_passes
    ADD CONSTRAINT gate_passes_gate_pass_number_unique UNIQUE (gate_pass_number);

--
-- Name: gate_passes gate_passes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gate_passes
    ADD CONSTRAINT gate_passes_pkey PRIMARY KEY (id);

--
-- Name: iar_items iar_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iar_items
    ADD CONSTRAINT iar_items_pkey PRIMARY KEY (id);

--
-- Name: inspection_acceptance_reports inspection_acceptance_reports_iar_number_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inspection_acceptance_reports
    ADD CONSTRAINT inspection_acceptance_reports_iar_number_unique UNIQUE (iar_number);

--
-- Name: inspection_acceptance_reports inspection_acceptance_reports_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inspection_acceptance_reports
    ADD CONSTRAINT inspection_acceptance_reports_pkey PRIMARY KEY (id);

--
-- Name: inventory_logs inventory_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_logs
    ADD CONSTRAINT inventory_logs_pkey PRIMARY KEY (id);

--
-- Name: items items_code_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.items
    ADD CONSTRAINT items_code_unique UNIQUE (code);

--
-- Name: items items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.items
    ADD CONSTRAINT items_pkey PRIMARY KEY (id);

--
-- Name: login_attempts login_attempts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.login_attempts
    ADD CONSTRAINT login_attempts_pkey PRIMARY KEY (id);

--
-- Name: notifications notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);

--
-- Name: personal_access_tokens personal_access_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.personal_access_tokens
    ADD CONSTRAINT personal_access_tokens_pkey PRIMARY KEY (id);

--
-- Name: personal_access_tokens personal_access_tokens_token_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.personal_access_tokens
    ADD CONSTRAINT personal_access_tokens_token_unique UNIQUE (token);

--
-- Name: physical_inventory_count_items physical_inventory_count_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.physical_inventory_count_items
    ADD CONSTRAINT physical_inventory_count_items_pkey PRIMARY KEY (id);

--
-- Name: physical_inventory_counts physical_inventory_counts_count_number_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.physical_inventory_counts
    ADD CONSTRAINT physical_inventory_counts_count_number_unique UNIQUE (count_number);

--
-- Name: physical_inventory_counts physical_inventory_counts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.physical_inventory_counts
    ADD CONSTRAINT physical_inventory_counts_pkey PRIMARY KEY (id);

--
-- Name: physical_inventory_count_items pic_items_count_item_u; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.physical_inventory_count_items
    ADD CONSTRAINT pic_items_count_item_u UNIQUE (physical_inventory_count_id, item_id);

--
-- Name: ppmp_items ppmp_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ppmp_items
    ADD CONSTRAINT ppmp_items_pkey PRIMARY KEY (id);

--
-- Name: ppmps ppmps_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ppmps
    ADD CONSTRAINT ppmps_pkey PRIMARY KEY (id);

--
-- Name: ppmps ppmps_ppmp_number_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ppmps
    ADD CONSTRAINT ppmps_ppmp_number_unique UNIQUE (ppmp_number);

--
-- Name: procurement_milestones procurement_milestones_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.procurement_milestones
    ADD CONSTRAINT procurement_milestones_pkey PRIMARY KEY (id);

--
-- Name: properties properties_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.properties
    ADD CONSTRAINT properties_pkey PRIMARY KEY (id);

--
-- Name: properties properties_tag_number_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.properties
    ADD CONSTRAINT properties_tag_number_unique UNIQUE (tag_number);

--
-- Name: property_components property_components_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_components
    ADD CONSTRAINT property_components_pkey PRIMARY KEY (id);

--
-- Name: property_depreciation_logs property_depreciation_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_depreciation_logs
    ADD CONSTRAINT property_depreciation_logs_pkey PRIMARY KEY (id);

--
-- Name: property_transfers property_transfers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_transfers
    ADD CONSTRAINT property_transfers_pkey PRIMARY KEY (id);

--
-- Name: purchase_order_items purchase_order_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_order_items
    ADD CONSTRAINT purchase_order_items_pkey PRIMARY KEY (id);

--
-- Name: purchase_orders purchase_orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_orders
    ADD CONSTRAINT purchase_orders_pkey PRIMARY KEY (id);

--
-- Name: purchase_orders purchase_orders_po_number_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_orders
    ADD CONSTRAINT purchase_orders_po_number_unique UNIQUE (po_number);

--
-- Name: purchase_request_approvals purchase_request_approvals_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_request_approvals
    ADD CONSTRAINT purchase_request_approvals_pkey PRIMARY KEY (id);

--
-- Name: purchase_request_approvals purchase_request_approvals_purchase_request_id_step_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_request_approvals
    ADD CONSTRAINT purchase_request_approvals_purchase_request_id_step_unique UNIQUE (purchase_request_id, step);

--
-- Name: purchase_request_items purchase_request_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_request_items
    ADD CONSTRAINT purchase_request_items_pkey PRIMARY KEY (id);

--
-- Name: purchase_requests purchase_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_requests
    ADD CONSTRAINT purchase_requests_pkey PRIMARY KEY (id);

--
-- Name: purchase_requests purchase_requests_pr_number_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_requests
    ADD CONSTRAINT purchase_requests_pr_number_unique UNIQUE (pr_number);

--
-- Name: request_items request_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.request_items
    ADD CONSTRAINT request_items_pkey PRIMARY KEY (id);

--
-- Name: request_logs request_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.request_logs
    ADD CONSTRAINT request_logs_pkey PRIMARY KEY (id);

--
-- Name: requests requests_ics_number_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.requests
    ADD CONSTRAINT requests_ics_number_unique UNIQUE (ics_number);

--
-- Name: requests requests_par_number_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.requests
    ADD CONSTRAINT requests_par_number_unique UNIQUE (par_number);

--
-- Name: requests requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.requests
    ADD CONSTRAINT requests_pkey PRIMARY KEY (id);

--
-- Name: requests requests_request_number_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.requests
    ADD CONSTRAINT requests_request_number_unique UNIQUE (request_number);

--
-- Name: requests requests_ris_number_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.requests
    ADD CONSTRAINT requests_ris_number_unique UNIQUE (ris_number);

--
-- Name: rfq_items rfq_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rfq_items
    ADD CONSTRAINT rfq_items_pkey PRIMARY KEY (id);

--
-- Name: rfq_quotation_items rfq_quotation_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rfq_quotation_items
    ADD CONSTRAINT rfq_quotation_items_pkey PRIMARY KEY (id);

--
-- Name: rfq_quotation_items rfq_quotation_items_rfq_quotation_id_rfq_item_id_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rfq_quotation_items
    ADD CONSTRAINT rfq_quotation_items_rfq_quotation_id_rfq_item_id_unique UNIQUE (rfq_quotation_id, rfq_item_id);

--
-- Name: rfq_quotations rfq_quotations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rfq_quotations
    ADD CONSTRAINT rfq_quotations_pkey PRIMARY KEY (id);

--
-- Name: rfq_quotations rfq_quotations_rfq_id_supplier_id_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rfq_quotations
    ADD CONSTRAINT rfq_quotations_rfq_id_supplier_id_unique UNIQUE (rfq_id, supplier_id);

--
-- Name: rfqs rfqs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rfqs
    ADD CONSTRAINT rfqs_pkey PRIMARY KEY (id);

--
-- Name: rfqs rfqs_rfq_number_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rfqs
    ADD CONSTRAINT rfqs_rfq_number_unique UNIQUE (rfq_number);

--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (id);

--
-- Name: rsmi_reports rsmi_reports_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rsmi_reports
    ADD CONSTRAINT rsmi_reports_pkey PRIMARY KEY (id);

--
-- Name: rsmi_reports rsmi_reports_report_number_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rsmi_reports
    ADD CONSTRAINT rsmi_reports_report_number_unique UNIQUE (report_number);

--
-- Name: semi_expendable_issues semi_expendable_issues_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.semi_expendable_issues
    ADD CONSTRAINT semi_expendable_issues_pkey PRIMARY KEY (id);

--
-- Name: settings settings_key_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.settings
    ADD CONSTRAINT settings_key_unique UNIQUE (key);

--
-- Name: settings settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.settings
    ADD CONSTRAINT settings_pkey PRIMARY KEY (id);

--
-- Name: stock_adjustments stock_adjustments_adjustment_number_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_adjustments
    ADD CONSTRAINT stock_adjustments_adjustment_number_unique UNIQUE (adjustment_number);

--
-- Name: stock_adjustments stock_adjustments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_adjustments
    ADD CONSTRAINT stock_adjustments_pkey PRIMARY KEY (id);

--
-- Name: stock_transfers stock_transfers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_transfers
    ADD CONSTRAINT stock_transfers_pkey PRIMARY KEY (id);

--
-- Name: stock_transfers stock_transfers_transfer_number_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_transfers
    ADD CONSTRAINT stock_transfers_transfer_number_unique UNIQUE (transfer_number);

--
-- Name: stocks stocks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stocks
    ADD CONSTRAINT stocks_pkey PRIMARY KEY (id);

--
-- Name: supplier_blacklists supplier_blacklists_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplier_blacklists
    ADD CONSTRAINT supplier_blacklists_pkey PRIMARY KEY (id);

--
-- Name: supplier_compliance_documents supplier_compliance_documents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplier_compliance_documents
    ADD CONSTRAINT supplier_compliance_documents_pkey PRIMARY KEY (id);

--
-- Name: supplier_evaluations supplier_evaluations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplier_evaluations
    ADD CONSTRAINT supplier_evaluations_pkey PRIMARY KEY (id);

--
-- Name: suppliers suppliers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.suppliers
    ADD CONSTRAINT suppliers_pkey PRIMARY KEY (id);

--
-- Name: suppliers suppliers_vendor_code_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.suppliers
    ADD CONSTRAINT suppliers_vendor_code_unique UNIQUE (vendor_code);

--
-- Name: users users_email_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_unique UNIQUE (email);

--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);

--
-- Name: apcpi_assessments_fiscal_year_status_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX apcpi_assessments_fiscal_year_status_index ON public.apcpi_assessments USING btree (fiscal_year, status);

--
-- Name: apps_fiscal_year_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX apps_fiscal_year_index ON public.apps USING btree (fiscal_year);

--
-- Name: apps_status_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX apps_status_index ON public.apps USING btree (status);

--
-- Name: audit_trails_action_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX audit_trails_action_index ON public.audit_trails USING btree (action);

--
-- Name: audit_trails_auditable_type_auditable_id_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX audit_trails_auditable_type_auditable_id_index ON public.audit_trails USING btree (auditable_type, auditable_id);

--
-- Name: audit_trails_created_at_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX audit_trails_created_at_index ON public.audit_trails USING btree (created_at);

--
-- Name: bac_evaluations_purchase_request_id_stage_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX bac_evaluations_purchase_request_id_stage_index ON public.bac_evaluations USING btree (purchase_request_id, stage);

--
-- Name: bac_meetings_status_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX bac_meetings_status_index ON public.bac_meetings USING btree (status);

--
-- Name: bac_members_designation_is_active_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX bac_members_designation_is_active_index ON public.bac_members USING btree (designation, is_active);

--
-- Name: bac_resolutions_status_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX bac_resolutions_status_index ON public.bac_resolutions USING btree (status);

--
-- Name: bid_observers_bidding_id_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX bid_observers_bidding_id_index ON public.bid_observers USING btree (bidding_id);

--
-- Name: bid_protests_bidding_id_status_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX bid_protests_bidding_id_status_index ON public.bid_protests USING btree (bidding_id, status);

--
-- Name: biddings_status_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX biddings_status_index ON public.biddings USING btree (status);

--
-- Name: contract_securities_contract_id_security_type_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX contract_securities_contract_id_security_type_index ON public.contract_securities USING btree (contract_id, security_type);

--
-- Name: contracts_end_date_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX contracts_end_date_index ON public.contracts USING btree (end_date);

--
-- Name: contracts_status_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX contracts_status_index ON public.contracts USING btree (status);

--
-- Name: delivery_extensions_contract_id_status_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX delivery_extensions_contract_id_status_index ON public.delivery_extensions USING btree (contract_id, status);

--
-- Name: documents_document_number_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX documents_document_number_index ON public.documents USING btree (document_number);

--
-- Name: documents_documentable_type_documentable_id_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX documents_documentable_type_documentable_id_index ON public.documents USING btree (documentable_type, documentable_id);

--
-- Name: documents_type_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX documents_type_index ON public.documents USING btree (type);

--
-- Name: electronic_signatures_signable_type_signable_id_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX electronic_signatures_signable_type_signable_id_index ON public.electronic_signatures USING btree (signable_type, signable_id);

--
-- Name: esig_owner_purpose_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX esig_owner_purpose_idx ON public.electronic_signatures USING btree (signable_type, signable_id, purpose);

--
-- Name: framework_calloffs_contract_id_status_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX framework_calloffs_contract_id_status_index ON public.framework_calloffs USING btree (contract_id, status);

--
-- Name: inventory_logs_action_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX inventory_logs_action_index ON public.inventory_logs USING btree (action);

--
-- Name: inventory_logs_created_at_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX inventory_logs_created_at_index ON public.inventory_logs USING btree (created_at);

--
-- Name: inventory_logs_item_id_action_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX inventory_logs_item_id_action_index ON public.inventory_logs USING btree (item_id, action);

--
-- Name: inventory_logs_reference_type_reference_id_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX inventory_logs_reference_type_reference_id_index ON public.inventory_logs USING btree (reference_type, reference_id);

--
-- Name: login_attempts_email_created_at_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX login_attempts_email_created_at_index ON public.login_attempts USING btree (email, created_at);

--
-- Name: login_attempts_email_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX login_attempts_email_index ON public.login_attempts USING btree (email);

--
-- Name: login_attempts_ip_address_created_at_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX login_attempts_ip_address_created_at_index ON public.login_attempts USING btree (ip_address, created_at);

--
-- Name: notifications_related_type_related_id_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX notifications_related_type_related_id_index ON public.notifications USING btree (related_type, related_id);

--
-- Name: personal_access_tokens_tokenable_type_tokenable_id_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX personal_access_tokens_tokenable_type_tokenable_id_index ON public.personal_access_tokens USING btree (tokenable_type, tokenable_id);

--
-- Name: pic_items_count_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX pic_items_count_id ON public.physical_inventory_count_items USING btree (physical_inventory_count_id);

--
-- Name: pic_items_item_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX pic_items_item_id ON public.physical_inventory_count_items USING btree (item_id);

--
-- Name: pm_owner_seq_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX pm_owner_seq_idx ON public.procurement_milestones USING btree (milestoneable_type, milestoneable_id, sequence);

--
-- Name: ppmps_fiscal_year_department_id_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ppmps_fiscal_year_department_id_index ON public.ppmps USING btree (fiscal_year, department_id);

--
-- Name: ppmps_status_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ppmps_status_index ON public.ppmps USING btree (status);

--
-- Name: procurement_milestones_milestoneable_type_milestoneable_id_inde; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX procurement_milestones_milestoneable_type_milestoneable_id_inde ON public.procurement_milestones USING btree (milestoneable_type, milestoneable_id);

--
-- Name: properties_depreciation_method_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX properties_depreciation_method_index ON public.properties USING btree (depreciation_method);

--
-- Name: properties_is_depreciable_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX properties_is_depreciable_index ON public.properties USING btree (is_depreciable);

--
-- Name: property_depreciation_logs_depreciation_date_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX property_depreciation_logs_depreciation_date_index ON public.property_depreciation_logs USING btree (depreciation_date);

--
-- Name: property_depreciation_logs_property_id_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX property_depreciation_logs_property_id_index ON public.property_depreciation_logs USING btree (property_id);

--
-- Name: rfqs_status_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX rfqs_status_index ON public.rfqs USING btree (status);

--
-- Name: rsmi_reports_year_month_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX rsmi_reports_year_month_index ON public.rsmi_reports USING btree (year, month);

--
-- Name: semi_expendable_issues_status_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX semi_expendable_issues_status_index ON public.semi_expendable_issues USING btree (status);

--
-- Name: stock_adjustments_status_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX stock_adjustments_status_index ON public.stock_adjustments USING btree (status);

--
-- Name: stock_transfers_item_id_transfer_date_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX stock_transfers_item_id_transfer_date_index ON public.stock_transfers USING btree (item_id, transfer_date);

--
-- Name: stocks_iar_id_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX stocks_iar_id_index ON public.stocks USING btree (iar_id);

--
-- Name: supplier_blacklists_supplier_id_status_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX supplier_blacklists_supplier_id_status_index ON public.supplier_blacklists USING btree (supplier_id, status);

--
-- Name: supplier_compliance_documents_supplier_id_document_type_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX supplier_compliance_documents_supplier_id_document_type_index ON public.supplier_compliance_documents USING btree (supplier_id, document_type);

--
-- Name: apcpi_assessments apcpi_assessments_conducted_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.apcpi_assessments
    ADD CONSTRAINT apcpi_assessments_conducted_by_foreign FOREIGN KEY (conducted_by) REFERENCES public.users(id) ON DELETE SET NULL;

--
-- Name: apcpi_scores apcpi_scores_apcpi_assessment_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.apcpi_scores
    ADD CONSTRAINT apcpi_scores_apcpi_assessment_id_foreign FOREIGN KEY (apcpi_assessment_id) REFERENCES public.apcpi_assessments(id) ON DELETE CASCADE;

--
-- Name: app_items app_items_app_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_items
    ADD CONSTRAINT app_items_app_id_foreign FOREIGN KEY (app_id) REFERENCES public.apps(id) ON DELETE CASCADE;

--
-- Name: app_items app_items_department_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_items
    ADD CONSTRAINT app_items_department_id_foreign FOREIGN KEY (department_id) REFERENCES public.departments(id) ON DELETE SET NULL;

--
-- Name: app_items app_items_ppmp_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_items
    ADD CONSTRAINT app_items_ppmp_id_foreign FOREIGN KEY (ppmp_id) REFERENCES public.ppmps(id) ON DELETE SET NULL;

--
-- Name: apps apps_approved_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.apps
    ADD CONSTRAINT apps_approved_by_foreign FOREIGN KEY (approved_by) REFERENCES public.users(id) ON DELETE SET NULL;

--
-- Name: apps apps_prepared_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.apps
    ADD CONSTRAINT apps_prepared_by_foreign FOREIGN KEY (prepared_by) REFERENCES public.users(id) ON DELETE SET NULL;

--
-- Name: audit_trails audit_trails_user_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_trails
    ADD CONSTRAINT audit_trails_user_id_foreign FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE SET NULL;

--
-- Name: bac_evaluations bac_evaluations_bac_meeting_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bac_evaluations
    ADD CONSTRAINT bac_evaluations_bac_meeting_id_foreign FOREIGN KEY (bac_meeting_id) REFERENCES public.bac_meetings(id) ON DELETE SET NULL;

--
-- Name: bac_evaluations bac_evaluations_evaluated_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bac_evaluations
    ADD CONSTRAINT bac_evaluations_evaluated_by_foreign FOREIGN KEY (evaluated_by) REFERENCES public.users(id) ON DELETE SET NULL;

--
-- Name: bac_evaluations bac_evaluations_purchase_request_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bac_evaluations
    ADD CONSTRAINT bac_evaluations_purchase_request_id_foreign FOREIGN KEY (purchase_request_id) REFERENCES public.purchase_requests(id) ON DELETE SET NULL;

--
-- Name: bac_evaluations bac_evaluations_supplier_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bac_evaluations
    ADD CONSTRAINT bac_evaluations_supplier_id_foreign FOREIGN KEY (supplier_id) REFERENCES public.suppliers(id) ON DELETE SET NULL;

--
-- Name: bac_meeting_attendees bac_meeting_attendees_bac_meeting_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bac_meeting_attendees
    ADD CONSTRAINT bac_meeting_attendees_bac_meeting_id_foreign FOREIGN KEY (bac_meeting_id) REFERENCES public.bac_meetings(id) ON DELETE CASCADE;

--
-- Name: bac_meeting_attendees bac_meeting_attendees_user_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bac_meeting_attendees
    ADD CONSTRAINT bac_meeting_attendees_user_id_foreign FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE SET NULL;

--
-- Name: bac_meetings bac_meetings_called_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bac_meetings
    ADD CONSTRAINT bac_meetings_called_by_foreign FOREIGN KEY (called_by) REFERENCES public.users(id) ON DELETE SET NULL;

--
-- Name: bac_members bac_members_user_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bac_members
    ADD CONSTRAINT bac_members_user_id_foreign FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE SET NULL;

--
-- Name: bac_resolutions bac_resolutions_approved_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bac_resolutions
    ADD CONSTRAINT bac_resolutions_approved_by_foreign FOREIGN KEY (approved_by) REFERENCES public.users(id) ON DELETE SET NULL;

--
-- Name: bac_resolutions bac_resolutions_bac_meeting_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bac_resolutions
    ADD CONSTRAINT bac_resolutions_bac_meeting_id_foreign FOREIGN KEY (bac_meeting_id) REFERENCES public.bac_meetings(id) ON DELETE SET NULL;

--
-- Name: bac_resolutions bac_resolutions_prepared_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bac_resolutions
    ADD CONSTRAINT bac_resolutions_prepared_by_foreign FOREIGN KEY (prepared_by) REFERENCES public.users(id) ON DELETE SET NULL;

--
-- Name: bac_resolutions bac_resolutions_purchase_request_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bac_resolutions
    ADD CONSTRAINT bac_resolutions_purchase_request_id_foreign FOREIGN KEY (purchase_request_id) REFERENCES public.purchase_requests(id) ON DELETE SET NULL;

--
-- Name: bid_observers bid_observers_bidding_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bid_observers
    ADD CONSTRAINT bid_observers_bidding_id_foreign FOREIGN KEY (bidding_id) REFERENCES public.biddings(id) ON DELETE CASCADE;

--
-- Name: bid_protests bid_protests_bidding_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bid_protests
    ADD CONSTRAINT bid_protests_bidding_id_foreign FOREIGN KEY (bidding_id) REFERENCES public.biddings(id) ON DELETE CASCADE;

--
-- Name: bid_protests bid_protests_created_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bid_protests
    ADD CONSTRAINT bid_protests_created_by_foreign FOREIGN KEY (created_by) REFERENCES public.users(id) ON DELETE SET NULL;

--
-- Name: bid_protests bid_protests_supplier_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bid_protests
    ADD CONSTRAINT bid_protests_supplier_id_foreign FOREIGN KEY (supplier_id) REFERENCES public.suppliers(id) ON DELETE SET NULL;

--
-- Name: bidding_bidder_scores bidding_bidder_scores_bidding_bidder_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bidding_bidder_scores
    ADD CONSTRAINT bidding_bidder_scores_bidding_bidder_id_foreign FOREIGN KEY (bidding_bidder_id) REFERENCES public.bidding_bidders(id) ON DELETE CASCADE;

--
-- Name: bidding_bidder_scores bidding_bidder_scores_bidding_criterion_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bidding_bidder_scores
    ADD CONSTRAINT bidding_bidder_scores_bidding_criterion_id_foreign FOREIGN KEY (bidding_criterion_id) REFERENCES public.bidding_criteria(id) ON DELETE CASCADE;

--
-- Name: bidding_bidders bidding_bidders_bidding_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bidding_bidders
    ADD CONSTRAINT bidding_bidders_bidding_id_foreign FOREIGN KEY (bidding_id) REFERENCES public.biddings(id) ON DELETE CASCADE;

--
-- Name: bidding_bidders bidding_bidders_supplier_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bidding_bidders
    ADD CONSTRAINT bidding_bidders_supplier_id_foreign FOREIGN KEY (supplier_id) REFERENCES public.suppliers(id) ON DELETE CASCADE;

--
-- Name: bidding_criteria bidding_criteria_bidding_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bidding_criteria
    ADD CONSTRAINT bidding_criteria_bidding_id_foreign FOREIGN KEY (bidding_id) REFERENCES public.biddings(id) ON DELETE CASCADE;

--
-- Name: bidding_items bidding_items_bidding_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bidding_items
    ADD CONSTRAINT bidding_items_bidding_id_foreign FOREIGN KEY (bidding_id) REFERENCES public.biddings(id) ON DELETE CASCADE;

--
-- Name: bidding_items bidding_items_purchase_request_item_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bidding_items
    ADD CONSTRAINT bidding_items_purchase_request_item_id_foreign FOREIGN KEY (purchase_request_item_id) REFERENCES public.purchase_request_items(id) ON DELETE SET NULL;

--
-- Name: biddings biddings_awarded_supplier_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.biddings
    ADD CONSTRAINT biddings_awarded_supplier_id_foreign FOREIGN KEY (awarded_supplier_id) REFERENCES public.suppliers(id) ON DELETE SET NULL;

--
-- Name: biddings biddings_bac_resolution_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.biddings
    ADD CONSTRAINT biddings_bac_resolution_id_foreign FOREIGN KEY (bac_resolution_id) REFERENCES public.bac_resolutions(id) ON DELETE SET NULL;

--
-- Name: biddings biddings_prepared_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.biddings
    ADD CONSTRAINT biddings_prepared_by_foreign FOREIGN KEY (prepared_by) REFERENCES public.users(id) ON DELETE SET NULL;

--
-- Name: biddings biddings_purchase_request_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.biddings
    ADD CONSTRAINT biddings_purchase_request_id_foreign FOREIGN KEY (purchase_request_id) REFERENCES public.purchase_requests(id) ON DELETE SET NULL;

--
-- Name: categories categories_parent_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_parent_id_foreign FOREIGN KEY (parent_id) REFERENCES public.categories(id);

--
-- Name: contract_amendments contract_amendments_approved_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contract_amendments
    ADD CONSTRAINT contract_amendments_approved_by_foreign FOREIGN KEY (approved_by) REFERENCES public.users(id) ON DELETE SET NULL;

--
-- Name: contract_amendments contract_amendments_contract_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contract_amendments
    ADD CONSTRAINT contract_amendments_contract_id_foreign FOREIGN KEY (contract_id) REFERENCES public.contracts(id) ON DELETE CASCADE;

--
-- Name: contract_amendments contract_amendments_prepared_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contract_amendments
    ADD CONSTRAINT contract_amendments_prepared_by_foreign FOREIGN KEY (prepared_by) REFERENCES public.users(id) ON DELETE SET NULL;

--
-- Name: contract_securities contract_securities_contract_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contract_securities
    ADD CONSTRAINT contract_securities_contract_id_foreign FOREIGN KEY (contract_id) REFERENCES public.contracts(id) ON DELETE CASCADE;

--
-- Name: contracts contracts_bidding_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contracts
    ADD CONSTRAINT contracts_bidding_id_foreign FOREIGN KEY (bidding_id) REFERENCES public.biddings(id) ON DELETE SET NULL;

--
-- Name: contracts contracts_prepared_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contracts
    ADD CONSTRAINT contracts_prepared_by_foreign FOREIGN KEY (prepared_by) REFERENCES public.users(id) ON DELETE SET NULL;

--
-- Name: contracts contracts_purchase_order_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contracts
    ADD CONSTRAINT contracts_purchase_order_id_foreign FOREIGN KEY (purchase_order_id) REFERENCES public.purchase_orders(id) ON DELETE SET NULL;

--
-- Name: contracts contracts_purchase_request_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contracts
    ADD CONSTRAINT contracts_purchase_request_id_foreign FOREIGN KEY (purchase_request_id) REFERENCES public.purchase_requests(id) ON DELETE SET NULL;

--
-- Name: contracts contracts_rfq_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contracts
    ADD CONSTRAINT contracts_rfq_id_foreign FOREIGN KEY (rfq_id) REFERENCES public.rfqs(id) ON DELETE SET NULL;

--
-- Name: contracts contracts_supplier_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contracts
    ADD CONSTRAINT contracts_supplier_id_foreign FOREIGN KEY (supplier_id) REFERENCES public.suppliers(id) ON DELETE CASCADE;

--
-- Name: delivery_extensions delivery_extensions_approved_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_extensions
    ADD CONSTRAINT delivery_extensions_approved_by_foreign FOREIGN KEY (approved_by) REFERENCES public.users(id) ON DELETE SET NULL;

--
-- Name: delivery_extensions delivery_extensions_contract_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_extensions
    ADD CONSTRAINT delivery_extensions_contract_id_foreign FOREIGN KEY (contract_id) REFERENCES public.contracts(id) ON DELETE CASCADE;

--
-- Name: disposal_notifications disposal_notifications_item_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.disposal_notifications
    ADD CONSTRAINT disposal_notifications_item_id_foreign FOREIGN KEY (item_id) REFERENCES public.items(id);

--
-- Name: disposal_requests disposal_requests_approved_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.disposal_requests
    ADD CONSTRAINT disposal_requests_approved_by_foreign FOREIGN KEY (approved_by) REFERENCES public.users(id);

--
-- Name: disposal_requests disposal_requests_item_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.disposal_requests
    ADD CONSTRAINT disposal_requests_item_id_foreign FOREIGN KEY (item_id) REFERENCES public.items(id) ON DELETE SET NULL;

--
-- Name: disposal_requests disposal_requests_property_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.disposal_requests
    ADD CONSTRAINT disposal_requests_property_id_foreign FOREIGN KEY (property_id) REFERENCES public.properties(id);

--
-- Name: disposal_requests disposal_requests_requested_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.disposal_requests
    ADD CONSTRAINT disposal_requests_requested_by_foreign FOREIGN KEY (requested_by) REFERENCES public.users(id);

--
-- Name: document_versions document_versions_document_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document_versions
    ADD CONSTRAINT document_versions_document_id_foreign FOREIGN KEY (document_id) REFERENCES public.documents(id) ON DELETE CASCADE;

--
-- Name: document_versions document_versions_uploaded_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document_versions
    ADD CONSTRAINT document_versions_uploaded_by_foreign FOREIGN KEY (uploaded_by) REFERENCES public.users(id) ON DELETE SET NULL;

--
-- Name: documents documents_uploaded_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documents
    ADD CONSTRAINT documents_uploaded_by_foreign FOREIGN KEY (uploaded_by) REFERENCES public.users(id) ON DELETE SET NULL;

--
-- Name: electronic_signatures electronic_signatures_user_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.electronic_signatures
    ADD CONSTRAINT electronic_signatures_user_id_foreign FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE SET NULL;

--
-- Name: framework_calloffs framework_calloffs_contract_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.framework_calloffs
    ADD CONSTRAINT framework_calloffs_contract_id_foreign FOREIGN KEY (contract_id) REFERENCES public.contracts(id) ON DELETE CASCADE;

--
-- Name: framework_calloffs framework_calloffs_created_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.framework_calloffs
    ADD CONSTRAINT framework_calloffs_created_by_foreign FOREIGN KEY (created_by) REFERENCES public.users(id) ON DELETE SET NULL;

--
-- Name: framework_calloffs framework_calloffs_supplier_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.framework_calloffs
    ADD CONSTRAINT framework_calloffs_supplier_id_foreign FOREIGN KEY (supplier_id) REFERENCES public.suppliers(id) ON DELETE SET NULL;

--
-- Name: gate_passes gate_passes_property_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gate_passes
    ADD CONSTRAINT gate_passes_property_id_foreign FOREIGN KEY (property_id) REFERENCES public.properties(id);

--
-- Name: gate_passes gate_passes_received_back_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gate_passes
    ADD CONSTRAINT gate_passes_received_back_by_foreign FOREIGN KEY (received_back_by) REFERENCES public.users(id);

--
-- Name: gate_passes gate_passes_released_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gate_passes
    ADD CONSTRAINT gate_passes_released_by_foreign FOREIGN KEY (released_by) REFERENCES public.users(id);

--
-- Name: iar_items iar_items_inspection_acceptance_report_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iar_items
    ADD CONSTRAINT iar_items_inspection_acceptance_report_id_foreign FOREIGN KEY (inspection_acceptance_report_id) REFERENCES public.inspection_acceptance_reports(id) ON DELETE CASCADE;

--
-- Name: iar_items iar_items_item_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iar_items
    ADD CONSTRAINT iar_items_item_id_foreign FOREIGN KEY (item_id) REFERENCES public.items(id) ON DELETE SET NULL;

--
-- Name: iar_items iar_items_purchase_order_item_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iar_items
    ADD CONSTRAINT iar_items_purchase_order_item_id_foreign FOREIGN KEY (purchase_order_item_id) REFERENCES public.purchase_order_items(id) ON DELETE SET NULL;

--
-- Name: inspection_acceptance_reports inspection_acceptance_reports_accepted_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inspection_acceptance_reports
    ADD CONSTRAINT inspection_acceptance_reports_accepted_by_foreign FOREIGN KEY (accepted_by) REFERENCES public.users(id) ON DELETE SET NULL;

--
-- Name: inspection_acceptance_reports inspection_acceptance_reports_inspected_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inspection_acceptance_reports
    ADD CONSTRAINT inspection_acceptance_reports_inspected_by_foreign FOREIGN KEY (inspected_by) REFERENCES public.users(id);

--
-- Name: inspection_acceptance_reports inspection_acceptance_reports_purchase_order_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inspection_acceptance_reports
    ADD CONSTRAINT inspection_acceptance_reports_purchase_order_id_foreign FOREIGN KEY (purchase_order_id) REFERENCES public.purchase_orders(id);

--
-- Name: inventory_logs inventory_logs_item_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_logs
    ADD CONSTRAINT inventory_logs_item_id_foreign FOREIGN KEY (item_id) REFERENCES public.items(id) ON DELETE CASCADE;

--
-- Name: inventory_logs inventory_logs_user_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_logs
    ADD CONSTRAINT inventory_logs_user_id_foreign FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

--
-- Name: items items_category_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.items
    ADD CONSTRAINT items_category_id_foreign FOREIGN KEY (category_id) REFERENCES public.categories(id);

--
-- Name: notifications notifications_user_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_user_id_foreign FOREIGN KEY (user_id) REFERENCES public.users(id);

--
-- Name: physical_inventory_counts physical_inventory_counts_initiated_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.physical_inventory_counts
    ADD CONSTRAINT physical_inventory_counts_initiated_by_foreign FOREIGN KEY (initiated_by) REFERENCES public.users(id);

--
-- Name: physical_inventory_count_items pic_items_count_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.physical_inventory_count_items
    ADD CONSTRAINT pic_items_count_fk FOREIGN KEY (physical_inventory_count_id) REFERENCES public.physical_inventory_counts(id) ON DELETE CASCADE;

--
-- Name: physical_inventory_count_items pic_items_item_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.physical_inventory_count_items
    ADD CONSTRAINT pic_items_item_fk FOREIGN KEY (item_id) REFERENCES public.items(id) ON DELETE CASCADE;

--
-- Name: ppmp_items ppmp_items_ppmp_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ppmp_items
    ADD CONSTRAINT ppmp_items_ppmp_id_foreign FOREIGN KEY (ppmp_id) REFERENCES public.ppmps(id) ON DELETE CASCADE;

--
-- Name: ppmps ppmps_approved_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ppmps
    ADD CONSTRAINT ppmps_approved_by_foreign FOREIGN KEY (approved_by) REFERENCES public.users(id) ON DELETE SET NULL;

--
-- Name: ppmps ppmps_department_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ppmps
    ADD CONSTRAINT ppmps_department_id_foreign FOREIGN KEY (department_id) REFERENCES public.departments(id) ON DELETE CASCADE;

--
-- Name: ppmps ppmps_prepared_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ppmps
    ADD CONSTRAINT ppmps_prepared_by_foreign FOREIGN KEY (prepared_by) REFERENCES public.users(id) ON DELETE SET NULL;

--
-- Name: ppmps ppmps_reviewed_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ppmps
    ADD CONSTRAINT ppmps_reviewed_by_foreign FOREIGN KEY (reviewed_by) REFERENCES public.users(id) ON DELETE SET NULL;

--
-- Name: properties properties_assigned_to_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.properties
    ADD CONSTRAINT properties_assigned_to_foreign FOREIGN KEY (assigned_to) REFERENCES public.users(id);

--
-- Name: properties properties_item_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.properties
    ADD CONSTRAINT properties_item_id_foreign FOREIGN KEY (item_id) REFERENCES public.items(id);

--
-- Name: properties properties_parent_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.properties
    ADD CONSTRAINT properties_parent_id_foreign FOREIGN KEY (parent_id) REFERENCES public.properties(id) ON DELETE SET NULL;

--
-- Name: properties properties_stock_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.properties
    ADD CONSTRAINT properties_stock_id_foreign FOREIGN KEY (stock_id) REFERENCES public.stocks(id) ON DELETE SET NULL;

--
-- Name: property_components property_components_property_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_components
    ADD CONSTRAINT property_components_property_id_foreign FOREIGN KEY (property_id) REFERENCES public.properties(id) ON DELETE CASCADE;

--
-- Name: property_components property_components_stock_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_components
    ADD CONSTRAINT property_components_stock_id_foreign FOREIGN KEY (stock_id) REFERENCES public.stocks(id) ON DELETE SET NULL;

--
-- Name: property_depreciation_logs property_depreciation_logs_property_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_depreciation_logs
    ADD CONSTRAINT property_depreciation_logs_property_id_foreign FOREIGN KEY (property_id) REFERENCES public.properties(id) ON DELETE CASCADE;

--
-- Name: property_transfers property_transfers_approved_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_transfers
    ADD CONSTRAINT property_transfers_approved_by_foreign FOREIGN KEY (approved_by) REFERENCES public.users(id);

--
-- Name: property_transfers property_transfers_from_user_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_transfers
    ADD CONSTRAINT property_transfers_from_user_foreign FOREIGN KEY (from_user) REFERENCES public.users(id);

--
-- Name: property_transfers property_transfers_property_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_transfers
    ADD CONSTRAINT property_transfers_property_id_foreign FOREIGN KEY (property_id) REFERENCES public.properties(id);

--
-- Name: property_transfers property_transfers_to_user_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_transfers
    ADD CONSTRAINT property_transfers_to_user_foreign FOREIGN KEY (to_user) REFERENCES public.users(id);

--
-- Name: purchase_order_items purchase_order_items_purchase_order_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_order_items
    ADD CONSTRAINT purchase_order_items_purchase_order_id_foreign FOREIGN KEY (purchase_order_id) REFERENCES public.purchase_orders(id) ON DELETE CASCADE;

--
-- Name: purchase_orders purchase_orders_approved_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_orders
    ADD CONSTRAINT purchase_orders_approved_by_foreign FOREIGN KEY (approved_by) REFERENCES public.users(id);

--
-- Name: purchase_orders purchase_orders_bidding_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_orders
    ADD CONSTRAINT purchase_orders_bidding_id_foreign FOREIGN KEY (bidding_id) REFERENCES public.biddings(id) ON DELETE SET NULL;

--
-- Name: purchase_orders purchase_orders_purchase_request_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_orders
    ADD CONSTRAINT purchase_orders_purchase_request_id_foreign FOREIGN KEY (purchase_request_id) REFERENCES public.purchase_requests(id);

--
-- Name: purchase_orders purchase_orders_rfq_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_orders
    ADD CONSTRAINT purchase_orders_rfq_id_foreign FOREIGN KEY (rfq_id) REFERENCES public.rfqs(id) ON DELETE SET NULL;

--
-- Name: purchase_orders purchase_orders_supplier_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_orders
    ADD CONSTRAINT purchase_orders_supplier_id_foreign FOREIGN KEY (supplier_id) REFERENCES public.suppliers(id);

--
-- Name: purchase_request_approvals purchase_request_approvals_acted_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_request_approvals
    ADD CONSTRAINT purchase_request_approvals_acted_by_foreign FOREIGN KEY (acted_by) REFERENCES public.users(id) ON DELETE SET NULL;

--
-- Name: purchase_request_approvals purchase_request_approvals_purchase_request_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_request_approvals
    ADD CONSTRAINT purchase_request_approvals_purchase_request_id_foreign FOREIGN KEY (purchase_request_id) REFERENCES public.purchase_requests(id) ON DELETE CASCADE;

--
-- Name: purchase_request_items purchase_request_items_app_item_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_request_items
    ADD CONSTRAINT purchase_request_items_app_item_id_foreign FOREIGN KEY (app_item_id) REFERENCES public.app_items(id) ON DELETE SET NULL;

--
-- Name: purchase_request_items purchase_request_items_purchase_request_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_request_items
    ADD CONSTRAINT purchase_request_items_purchase_request_id_foreign FOREIGN KEY (purchase_request_id) REFERENCES public.purchase_requests(id) ON DELETE CASCADE;

--
-- Name: purchase_requests purchase_requests_app_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_requests
    ADD CONSTRAINT purchase_requests_app_id_foreign FOREIGN KEY (app_id) REFERENCES public.apps(id) ON DELETE SET NULL;

--
-- Name: purchase_requests purchase_requests_caf_certified_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_requests
    ADD CONSTRAINT purchase_requests_caf_certified_by_foreign FOREIGN KEY (caf_certified_by) REFERENCES public.users(id) ON DELETE SET NULL;

--
-- Name: purchase_requests purchase_requests_department_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_requests
    ADD CONSTRAINT purchase_requests_department_id_foreign FOREIGN KEY (department_id) REFERENCES public.departments(id);

--
-- Name: purchase_requests purchase_requests_requested_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_requests
    ADD CONSTRAINT purchase_requests_requested_by_foreign FOREIGN KEY (requested_by) REFERENCES public.users(id);

--
-- Name: request_items request_items_item_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.request_items
    ADD CONSTRAINT request_items_item_id_foreign FOREIGN KEY (item_id) REFERENCES public.items(id);

--
-- Name: request_items request_items_request_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.request_items
    ADD CONSTRAINT request_items_request_id_foreign FOREIGN KEY (request_id) REFERENCES public.requests(id);

--
-- Name: request_logs request_logs_department_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.request_logs
    ADD CONSTRAINT request_logs_department_id_foreign FOREIGN KEY (department_id) REFERENCES public.departments(id);

--
-- Name: request_logs request_logs_item_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.request_logs
    ADD CONSTRAINT request_logs_item_id_foreign FOREIGN KEY (item_id) REFERENCES public.items(id);

--
-- Name: request_logs request_logs_requested_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.request_logs
    ADD CONSTRAINT request_logs_requested_by_foreign FOREIGN KEY (requested_by) REFERENCES public.users(id);

--
-- Name: requests requests_department_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.requests
    ADD CONSTRAINT requests_department_id_foreign FOREIGN KEY (department_id) REFERENCES public.departments(id);

--
-- Name: requests requests_requested_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.requests
    ADD CONSTRAINT requests_requested_by_foreign FOREIGN KEY (requested_by) REFERENCES public.users(id);

--
-- Name: rfq_items rfq_items_purchase_request_item_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rfq_items
    ADD CONSTRAINT rfq_items_purchase_request_item_id_foreign FOREIGN KEY (purchase_request_item_id) REFERENCES public.purchase_request_items(id) ON DELETE SET NULL;

--
-- Name: rfq_items rfq_items_rfq_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rfq_items
    ADD CONSTRAINT rfq_items_rfq_id_foreign FOREIGN KEY (rfq_id) REFERENCES public.rfqs(id) ON DELETE CASCADE;

--
-- Name: rfq_quotation_items rfq_quotation_items_rfq_item_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rfq_quotation_items
    ADD CONSTRAINT rfq_quotation_items_rfq_item_id_foreign FOREIGN KEY (rfq_item_id) REFERENCES public.rfq_items(id) ON DELETE CASCADE;

--
-- Name: rfq_quotation_items rfq_quotation_items_rfq_quotation_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rfq_quotation_items
    ADD CONSTRAINT rfq_quotation_items_rfq_quotation_id_foreign FOREIGN KEY (rfq_quotation_id) REFERENCES public.rfq_quotations(id) ON DELETE CASCADE;

--
-- Name: rfq_quotations rfq_quotations_rfq_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rfq_quotations
    ADD CONSTRAINT rfq_quotations_rfq_id_foreign FOREIGN KEY (rfq_id) REFERENCES public.rfqs(id) ON DELETE CASCADE;

--
-- Name: rfq_quotations rfq_quotations_supplier_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rfq_quotations
    ADD CONSTRAINT rfq_quotations_supplier_id_foreign FOREIGN KEY (supplier_id) REFERENCES public.suppliers(id) ON DELETE CASCADE;

--
-- Name: rfqs rfqs_awarded_supplier_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rfqs
    ADD CONSTRAINT rfqs_awarded_supplier_id_foreign FOREIGN KEY (awarded_supplier_id) REFERENCES public.suppliers(id) ON DELETE SET NULL;

--
-- Name: rfqs rfqs_prepared_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rfqs
    ADD CONSTRAINT rfqs_prepared_by_foreign FOREIGN KEY (prepared_by) REFERENCES public.users(id) ON DELETE SET NULL;

--
-- Name: rfqs rfqs_purchase_request_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rfqs
    ADD CONSTRAINT rfqs_purchase_request_id_foreign FOREIGN KEY (purchase_request_id) REFERENCES public.purchase_requests(id) ON DELETE SET NULL;

--
-- Name: rsmi_reports rsmi_reports_generated_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rsmi_reports
    ADD CONSTRAINT rsmi_reports_generated_by_foreign FOREIGN KEY (generated_by) REFERENCES public.users(id);

--
-- Name: semi_expendable_issues semi_expendable_issues_department_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.semi_expendable_issues
    ADD CONSTRAINT semi_expendable_issues_department_id_foreign FOREIGN KEY (department_id) REFERENCES public.departments(id) ON DELETE SET NULL;

--
-- Name: semi_expendable_issues semi_expendable_issues_issued_to_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.semi_expendable_issues
    ADD CONSTRAINT semi_expendable_issues_issued_to_foreign FOREIGN KEY (issued_to) REFERENCES public.users(id) ON DELETE SET NULL;

--
-- Name: semi_expendable_issues semi_expendable_issues_item_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.semi_expendable_issues
    ADD CONSTRAINT semi_expendable_issues_item_id_foreign FOREIGN KEY (item_id) REFERENCES public.items(id) ON DELETE CASCADE;

--
-- Name: semi_expendable_issues semi_expendable_issues_request_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.semi_expendable_issues
    ADD CONSTRAINT semi_expendable_issues_request_id_foreign FOREIGN KEY (request_id) REFERENCES public.requests(id) ON DELETE SET NULL;

--
-- Name: stock_adjustments stock_adjustments_approved_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_adjustments
    ADD CONSTRAINT stock_adjustments_approved_by_foreign FOREIGN KEY (approved_by) REFERENCES public.users(id) ON DELETE SET NULL;

--
-- Name: stock_adjustments stock_adjustments_item_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_adjustments
    ADD CONSTRAINT stock_adjustments_item_id_foreign FOREIGN KEY (item_id) REFERENCES public.items(id) ON DELETE CASCADE;

--
-- Name: stock_adjustments stock_adjustments_requested_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_adjustments
    ADD CONSTRAINT stock_adjustments_requested_by_foreign FOREIGN KEY (requested_by) REFERENCES public.users(id) ON DELETE SET NULL;

--
-- Name: stock_transfers stock_transfers_item_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_transfers
    ADD CONSTRAINT stock_transfers_item_id_foreign FOREIGN KEY (item_id) REFERENCES public.items(id) ON DELETE CASCADE;

--
-- Name: stock_transfers stock_transfers_stock_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_transfers
    ADD CONSTRAINT stock_transfers_stock_id_foreign FOREIGN KEY (stock_id) REFERENCES public.stocks(id) ON DELETE SET NULL;

--
-- Name: stock_transfers stock_transfers_transferred_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_transfers
    ADD CONSTRAINT stock_transfers_transferred_by_foreign FOREIGN KEY (transferred_by) REFERENCES public.users(id) ON DELETE SET NULL;

--
-- Name: stocks stocks_iar_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stocks
    ADD CONSTRAINT stocks_iar_id_foreign FOREIGN KEY (iar_id) REFERENCES public.inspection_acceptance_reports(id) ON DELETE SET NULL;

--
-- Name: stocks stocks_item_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stocks
    ADD CONSTRAINT stocks_item_id_foreign FOREIGN KEY (item_id) REFERENCES public.items(id);

--
-- Name: stocks stocks_supplier_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stocks
    ADD CONSTRAINT stocks_supplier_id_foreign FOREIGN KEY (supplier_id) REFERENCES public.suppliers(id);

--
-- Name: supplier_blacklists supplier_blacklists_created_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplier_blacklists
    ADD CONSTRAINT supplier_blacklists_created_by_foreign FOREIGN KEY (created_by) REFERENCES public.users(id) ON DELETE SET NULL;

--
-- Name: supplier_blacklists supplier_blacklists_supplier_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplier_blacklists
    ADD CONSTRAINT supplier_blacklists_supplier_id_foreign FOREIGN KEY (supplier_id) REFERENCES public.suppliers(id) ON DELETE CASCADE;

--
-- Name: supplier_compliance_documents supplier_compliance_documents_document_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplier_compliance_documents
    ADD CONSTRAINT supplier_compliance_documents_document_id_foreign FOREIGN KEY (document_id) REFERENCES public.documents(id) ON DELETE SET NULL;

--
-- Name: supplier_compliance_documents supplier_compliance_documents_supplier_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplier_compliance_documents
    ADD CONSTRAINT supplier_compliance_documents_supplier_id_foreign FOREIGN KEY (supplier_id) REFERENCES public.suppliers(id) ON DELETE CASCADE;

--
-- Name: supplier_evaluations supplier_evaluations_evaluated_by_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplier_evaluations
    ADD CONSTRAINT supplier_evaluations_evaluated_by_foreign FOREIGN KEY (evaluated_by) REFERENCES public.users(id) ON DELETE SET NULL;

--
-- Name: supplier_evaluations supplier_evaluations_supplier_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplier_evaluations
    ADD CONSTRAINT supplier_evaluations_supplier_id_foreign FOREIGN KEY (supplier_id) REFERENCES public.suppliers(id) ON DELETE CASCADE;

--
-- Name: users users_department_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_department_id_foreign FOREIGN KEY (department_id) REFERENCES public.departments(id);

--
-- Name: users users_role_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_role_id_foreign FOREIGN KEY (role_id) REFERENCES public.roles(id);

--
-- PostgreSQL database dump complete
--

