-- Procurement of common-use supplies and equipment (CSE) from the Procurement Service of the DBM
-- (LOI 755, EO 359 s. 1989, AO 17 s. 2011): the PS price catalogue, and Agency Procurement Requests
-- (APR) placed through the PhilGEPS Virtual Store, paid ahead, and delivered or picked up against a
-- PS Delivery Receipt. A line PS cannot supply carries the Certificate of Non-Availability of Stock.

CREATE TABLE ps_catalogue_items (
    id bigserial PRIMARY KEY,
    code varchar(50) NOT NULL,
    description varchar(500) NOT NULL,
    unit varchar(50) NOT NULL,
    unit_price numeric(15,2) NOT NULL CHECK (unit_price >= 0),
    item_id bigint REFERENCES items(id),
    is_active boolean NOT NULL DEFAULT true,
    price_as_of date,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);
CREATE UNIQUE INDEX ps_catalogue_items_code_unique ON ps_catalogue_items (code);

CREATE TABLE ps_orders (
    id bigserial PRIMARY KEY,
    apr_number varchar(50) NOT NULL,
    purchase_request_id bigint NOT NULL REFERENCES purchase_requests(id),
    status varchar(20) NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'submitted', 'paid', 'delivered', 'cancelled')),
    vs_reference varchar(100),
    submitted_date date,
    payment_mode varchar(30) CHECK (payment_mode IN ('cash', 'lddap_ada', 'check', 'unutilized_funds')),
    payment_reference varchar(100),
    payment_date date,
    amount_paid numeric(15,2),
    dr_number varchar(100),
    dr_date date,
    delivery_mode varchar(20) CHECK (delivery_mode IN ('pickup', 'delivery')),
    purchase_order_id bigint REFERENCES purchase_orders(id),
    remarks text,
    prepared_by bigint REFERENCES users(id),
    total_amount numeric(15,2) NOT NULL DEFAULT 0,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    deleted_at timestamp(0) without time zone
);
CREATE UNIQUE INDEX ps_orders_apr_number_unique ON ps_orders (apr_number);
CREATE INDEX ps_orders_purchase_request_id_index ON ps_orders (purchase_request_id);

CREATE TABLE ps_order_items (
    id bigserial PRIMARY KEY,
    ps_order_id bigint NOT NULL REFERENCES ps_orders(id),
    purchase_request_item_id bigint NOT NULL REFERENCES purchase_request_items(id),
    ps_catalogue_item_id bigint REFERENCES ps_catalogue_items(id),
    description varchar(500) NOT NULL,
    unit varchar(50),
    quantity integer NOT NULL CHECK (quantity > 0),
    unit_price numeric(15,2) NOT NULL,
    amount numeric(15,2) NOT NULL,
    availability varchar(20) NOT NULL DEFAULT 'available' CHECK (availability IN ('available', 'not_available')),
    cnas_number varchar(100),
    cnas_date date,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);
CREATE INDEX ps_order_items_order_index ON ps_order_items (ps_order_id);
CREATE INDEX ps_order_items_pr_item_index ON ps_order_items (purchase_request_item_id);

ALTER TABLE purchase_orders ADD COLUMN ps_order_id bigint REFERENCES ps_orders(id);
