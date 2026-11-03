-- Other systems' APIs that PAMS reads and shows, read-only: first their payment records (e.g. the accounting
-- system's checks and LDDAP-ADAs). The secret is stored encrypted and never sent back to the browser.
CREATE TABLE external_apis (
    id bigserial PRIMARY KEY,
    name varchar(120) NOT NULL UNIQUE,
    description text,
    category varchar(30) NOT NULL DEFAULT 'payments',
    base_url varchar(500) NOT NULL,
    path varchar(500) NOT NULL DEFAULT '',
    auth_type varchar(20) NOT NULL DEFAULT 'none',
    auth_header varchar(100),
    secret_cipher text,
    data_path varchar(200),
    columns text,
    context varchar(20) NOT NULL DEFAULT 'none',
    timeout_seconds integer NOT NULL DEFAULT 10,
    is_active boolean NOT NULL DEFAULT true,
    last_status varchar(300),
    last_called_at timestamp(0) without time zone,
    created_by bigint,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    CONSTRAINT external_apis_auth_type_check CHECK (auth_type IN ('none', 'bearer', 'header', 'basic')),
    CONSTRAINT external_apis_context_check CHECK (context IN ('none', 'payable'))
);
