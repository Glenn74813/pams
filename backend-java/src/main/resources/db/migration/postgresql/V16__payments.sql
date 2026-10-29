-- TOR B 7.4: payment schedules on contracts and purchase orders, and the petty cash fund.
-- A tranche belongs to exactly one contract or one purchase order and moves
-- pending -> for_payment -> paid. Deductions are recorded when it is paid; amount_paid is the
-- net released (amount less retention, liquidated damages and withholding tax).
CREATE TABLE payment_tranches (
    id                    bigserial PRIMARY KEY,
    contract_id           bigint REFERENCES contracts (id) ON DELETE CASCADE,
    purchase_order_id     bigint REFERENCES purchase_orders (id) ON DELETE CASCADE,
    sequence_no           integer        NOT NULL,
    description           varchar(255)   NOT NULL,
    percentage            numeric(7, 4),
    amount                numeric(18, 2) NOT NULL,
    due_date              date,
    deliverable           text,
    responsibility_center varchar(100),
    mfo_pap               varchar(255),
    uacs_code             varchar(50),
    status                varchar(20)    NOT NULL DEFAULT 'pending',
    ors_number            varchar(50),
    dv_number             varchar(50),
    created_by            bigint REFERENCES users (id) ON DELETE SET NULL,
    approved_by           bigint REFERENCES users (id) ON DELETE SET NULL,
    approved_at           timestamp(0),
    paid_by               bigint REFERENCES users (id) ON DELETE SET NULL,
    paid_at               timestamp(0),
    paid_date             date,
    payment_method        varchar(20),
    payment_reference     varchar(100),
    amount_paid           numeric(18, 2),
    retention_amount      numeric(18, 2) NOT NULL DEFAULT 0,
    ld_deducted           numeric(18, 2) NOT NULL DEFAULT 0,
    withholding_tax       numeric(18, 2) NOT NULL DEFAULT 0,
    remarks               text,
    created_at            timestamp(0),
    updated_at            timestamp(0),
    CONSTRAINT payment_tranches_owner_check CHECK ((contract_id IS NOT NULL AND purchase_order_id IS NULL)
        OR (contract_id IS NULL AND purchase_order_id IS NOT NULL)),
    CONSTRAINT payment_tranches_status_check CHECK (status IN ('pending', 'for_payment', 'paid')),
    CONSTRAINT payment_tranches_method_check CHECK (payment_method IS NULL OR payment_method IN ('check', 'ada', 'lddap_ada')),
    CONSTRAINT payment_tranches_amount_check CHECK (amount > 0)
);
CREATE INDEX payment_tranches_contract_idx ON payment_tranches (contract_id);
CREATE INDEX payment_tranches_po_idx ON payment_tranches (purchase_order_id);
CREATE INDEX payment_tranches_due_idx ON payment_tranches (status, due_date);

-- A petty cash fund held by a custodian. Vouchers are recorded by the custodian and approved by
-- someone else; approved vouchers are bundled into a replenishment, which restores the fund once
-- the replenishing check/ADA is issued.
CREATE TABLE petty_cash_funds (
    id           bigserial PRIMARY KEY,
    fund_name    varchar(255)   NOT NULL,
    custodian_id bigint REFERENCES users (id) ON DELETE SET NULL,
    amount       numeric(18, 2) NOT NULL,
    fund_cluster varchar(50),
    status       varchar(20)    NOT NULL DEFAULT 'active',
    remarks      text,
    created_by   bigint REFERENCES users (id) ON DELETE SET NULL,
    created_at   timestamp(0),
    updated_at   timestamp(0),
    CONSTRAINT petty_cash_funds_status_check CHECK (status IN ('active', 'closed')),
    CONSTRAINT petty_cash_funds_amount_check CHECK (amount > 0)
);

CREATE TABLE petty_cash_replenishments (
    id                 bigserial PRIMARY KEY,
    petty_cash_fund_id bigint         NOT NULL REFERENCES petty_cash_funds (id) ON DELETE CASCADE,
    report_number      varchar(50)    NOT NULL,
    report_date        date           NOT NULL,
    total              numeric(18, 2) NOT NULL,
    status             varchar(20)    NOT NULL DEFAULT 'requested',
    requested_by       bigint REFERENCES users (id) ON DELETE SET NULL,
    replenished_by     bigint REFERENCES users (id) ON DELETE SET NULL,
    replenished_at     timestamp(0),
    replenished_date   date,
    reference          varchar(100),
    remarks            text,
    created_at         timestamp(0),
    updated_at         timestamp(0),
    CONSTRAINT petty_cash_replenishments_status_check CHECK (status IN ('requested', 'replenished'))
);
CREATE INDEX petty_cash_replenishments_fund_idx ON petty_cash_replenishments (petty_cash_fund_id);

CREATE TABLE petty_cash_vouchers (
    id                          bigserial PRIMARY KEY,
    petty_cash_fund_id          bigint         NOT NULL REFERENCES petty_cash_funds (id) ON DELETE CASCADE,
    pcv_number                  varchar(50)    NOT NULL,
    voucher_date                date           NOT NULL,
    payee                       varchar(255)   NOT NULL,
    particulars                 text           NOT NULL,
    amount                      numeric(18, 2) NOT NULL,
    responsibility_center       varchar(100),
    status                      varchar(20)    NOT NULL DEFAULT 'pending',
    recorded_by                 bigint REFERENCES users (id) ON DELETE SET NULL,
    approved_by                 bigint REFERENCES users (id) ON DELETE SET NULL,
    approved_at                 timestamp(0),
    petty_cash_replenishment_id bigint REFERENCES petty_cash_replenishments (id) ON DELETE SET NULL,
    remarks                     text,
    created_at                  timestamp(0),
    updated_at                  timestamp(0),
    CONSTRAINT petty_cash_vouchers_status_check CHECK (status IN ('pending', 'approved', 'cancelled')),
    CONSTRAINT petty_cash_vouchers_amount_check CHECK (amount > 0)
);
CREATE INDEX petty_cash_vouchers_fund_idx ON petty_cash_vouchers (petty_cash_fund_id);
CREATE INDEX petty_cash_vouchers_replenishment_idx ON petty_cash_vouchers (petty_cash_replenishment_id);

-- Give the new permissions to their natural holders, as V9 did for the PR chain: accounting and
-- budget prepare and release payments, the Admin (authorized representative of the head of agency)
-- approves them, and the Custodian keeps the petty cash. Auditors see everything.
UPDATE roles r SET permissions = (
    SELECT json_agg(DISTINCT p ORDER BY p) FROM (
        SELECT json_array_elements_text(r.permissions) AS p
        UNION SELECT unnest(g.extra)
    ) all_perms
), updated_at = now()
FROM (VALUES
    ('Admin',          ARRAY['view_payments', 'manage_payments', 'approve_payments', 'manage_petty_cash']),
    ('Accountant',     ARRAY['view_payments', 'manage_payments']),
    ('Budget Officer', ARRAY['view_payments', 'manage_payments']),
    ('Custodian',      ARRAY['view_payments', 'manage_petty_cash']),
    ('Auditor',        ARRAY['view_payments'])
) AS g(name, extra)
WHERE r.name = g.name AND r.is_system;
