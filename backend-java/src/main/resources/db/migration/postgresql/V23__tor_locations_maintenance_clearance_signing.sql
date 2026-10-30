-- NPC IMS Terms of Reference gaps: where each asset is kept (1.2.3, 1.4.7), a picture of each asset
-- and item (1.4.4, 1.5.4), maintenance schedules (1.4.5), property turnover and clearance
-- certificates (1.4.12, 1.5.5), document signing with certificates from a trusted CA such as the
-- PNPKI (1.4.11), and single sign-on (1.3.1).

CREATE TABLE asset_locations (
    id bigserial PRIMARY KEY,
    name varchar(150) NOT NULL,
    building varchar(150),
    floor varchar(50),
    room varchar(100),
    description varchar(500),
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);
CREATE UNIQUE INDEX asset_locations_name_unique ON asset_locations (name);

ALTER TABLE properties ADD COLUMN location_id bigint REFERENCES asset_locations(id);
ALTER TABLE properties ADD COLUMN photo_path varchar(255);
ALTER TABLE items ADD COLUMN photo_path varchar(255);
CREATE INDEX properties_location_id_index ON properties (location_id);

CREATE TABLE property_location_moves (
    id bigserial PRIMARY KEY,
    property_id bigint NOT NULL REFERENCES properties(id),
    from_location_id bigint REFERENCES asset_locations(id),
    to_location_id bigint REFERENCES asset_locations(id),
    moved_by bigint REFERENCES users(id),
    remarks varchar(500),
    created_at timestamp(0) without time zone
);
CREATE INDEX property_location_moves_property_id_index ON property_location_moves (property_id);

CREATE TABLE property_maintenance (
    id bigserial PRIMARY KEY,
    property_id bigint NOT NULL REFERENCES properties(id),
    maintenance_type varchar(20) NOT NULL CHECK (maintenance_type IN ('preventive', 'corrective', 'inspection', 'calibration')),
    description varchar(500) NOT NULL,
    scheduled_date date NOT NULL,
    interval_months integer CHECK (interval_months IS NULL OR interval_months BETWEEN 1 AND 120),
    status varchar(20) NOT NULL DEFAULT 'scheduled' CHECK (status IN ('scheduled', 'done', 'cancelled')),
    completed_date date,
    performed_by varchar(255),
    cost numeric(15,2) CHECK (cost IS NULL OR cost >= 0),
    remarks text,
    created_by bigint REFERENCES users(id),
    completed_by bigint REFERENCES users(id),
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);
CREATE INDEX property_maintenance_property_id_index ON property_maintenance (property_id);
CREATE INDEX property_maintenance_status_date_index ON property_maintenance (status, scheduled_date);

CREATE TABLE clearance_certificates (
    id bigserial PRIMARY KEY,
    certificate_number varchar(50),
    user_id bigint NOT NULL REFERENCES users(id),
    purpose varchar(30) NOT NULL CHECK (purpose IN ('resignation', 'retirement', 'transfer', 'end_of_contract', 'leave', 'other')),
    purpose_details varchar(255),
    effective_date date,
    status varchar(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'issued', 'cancelled')),
    requested_by bigint REFERENCES users(id),
    issued_by bigint REFERENCES users(id),
    issued_at timestamp(0) without time zone,
    assets_turned_over integer NOT NULL DEFAULT 0,
    remarks text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);
CREATE UNIQUE INDEX clearance_certificates_number_unique ON clearance_certificates (certificate_number) WHERE certificate_number IS NOT NULL;
CREATE INDEX clearance_certificates_user_id_index ON clearance_certificates (user_id);

CREATE TABLE trusted_certificates (
    id bigserial PRIMARY KEY,
    name varchar(150) NOT NULL,
    pem text NOT NULL,
    subject varchar(500) NOT NULL,
    fingerprint varchar(64) NOT NULL,
    not_after timestamp(0) without time zone,
    created_by bigint REFERENCES users(id),
    created_at timestamp(0) without time zone
);
CREATE UNIQUE INDEX trusted_certificates_fingerprint_unique ON trusted_certificates (fingerprint);

CREATE TABLE signing_requests (
    id bigserial PRIMARY KEY,
    request_number varchar(50) NOT NULL,
    title varchar(255) NOT NULL,
    file_name varchar(255) NOT NULL,
    file_path varchar(255) NOT NULL,
    content_type varchar(150),
    file_sha256 varchar(64) NOT NULL,
    file_size bigint NOT NULL,
    requested_by bigint NOT NULL REFERENCES users(id),
    signer_id bigint NOT NULL REFERENCES users(id),
    status varchar(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'signed', 'declined', 'cancelled')),
    platform_reference varchar(255),
    remarks text,
    signed_at timestamp(0) without time zone,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);
CREATE UNIQUE INDEX signing_requests_number_unique ON signing_requests (request_number);
CREATE INDEX signing_requests_signer_status_index ON signing_requests (signer_id, status);
CREATE INDEX signing_requests_sha256_index ON signing_requests (file_sha256);

CREATE TABLE document_signatures (
    id bigserial PRIMARY KEY,
    signing_request_id bigint NOT NULL REFERENCES signing_requests(id),
    signer_id bigint NOT NULL REFERENCES users(id),
    file_sha256 varchar(64) NOT NULL,
    algorithm varchar(30) NOT NULL,
    signature text NOT NULL,
    certificate_pem text NOT NULL,
    certificate_subject varchar(500) NOT NULL,
    certificate_issuer varchar(500) NOT NULL,
    certificate_serial varchar(100) NOT NULL,
    certificate_not_after timestamp(0) without time zone,
    trusted_certificate_id bigint REFERENCES trusted_certificates(id),
    source varchar(20) NOT NULL CHECK (source IN ('in_app', 'platform')),
    signed_at timestamp(0) without time zone NOT NULL
);
CREATE INDEX document_signatures_sha256_index ON document_signatures (file_sha256);

-- Single sign-on: the identity provider's stable subject id, linked at the first SSO login.
ALTER TABLE users ADD COLUMN sso_subject varchar(255);
CREATE UNIQUE INDEX users_sso_subject_unique ON users (sso_subject) WHERE sso_subject IS NOT NULL;
