-- TOR C.14: a physical count is either of stock-room supplies (RPCI), of PPE (RPCPPE) or of
-- semi-expendable property (RPCSP). Asset counts can be limited to one department's holders,
-- supply counts to one category.
ALTER TABLE physical_inventory_counts
    ADD COLUMN count_type varchar(20) NOT NULL DEFAULT 'supplies',
    ADD COLUMN department_id bigint REFERENCES departments (id) ON DELETE SET NULL,
    ADD COLUMN category_id bigint REFERENCES categories (id) ON DELETE SET NULL,
    ADD COLUMN started_at timestamp(0),
    -- Last stock-card entry before the cut-off; later entries are movements during the count.
    ADD COLUMN cutoff_log_id bigint,
    ADD COLUMN completed_by bigint REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN completed_at timestamp(0),
    ADD COLUMN verified_by bigint REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN verified_at timestamp(0),
    ADD CONSTRAINT physical_inventory_counts_type_check CHECK (count_type IN ('supplies', 'ppe', 'semi_expendable'));

-- Book quantities are frozen when the count starts (the cut-off), so lines exist before anyone
-- has counted them. The variance is posted through a stock adjustment on verification.
ALTER TABLE physical_inventory_count_items
    ALTER COLUMN counted_quantity DROP NOT NULL,
    ADD COLUMN unit_cost numeric(15, 2) NOT NULL DEFAULT 0,
    ADD COLUMN counted_by bigint REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN counted_at timestamp(0),
    ADD COLUMN stock_adjustment_id bigint REFERENCES stock_adjustments (id) ON DELETE SET NULL;

-- One line per property tag: the masterlist as of the cut-off, plus anything found on the floor
-- that the masterlist did not expect (in_masterlist = false: unknown tag; in_scope = false: an
-- asset on record under another department).
CREATE TABLE physical_count_properties (
    id                          bigserial PRIMARY KEY,
    physical_inventory_count_id bigint       NOT NULL REFERENCES physical_inventory_counts (id) ON DELETE CASCADE,
    property_id                 bigint REFERENCES properties (id) ON DELETE SET NULL,
    tag_number                  varchar(255) NOT NULL,
    description                 text,
    holder_id                   bigint REFERENCES users (id) ON DELETE SET NULL,
    department_id               bigint REFERENCES departments (id) ON DELETE SET NULL,
    book_value                  numeric(15, 2),
    condition_on_record         varchar(20),
    in_masterlist               boolean      NOT NULL DEFAULT true,
    in_scope                    boolean      NOT NULL DEFAULT true,
    result                      varchar(20)  NOT NULL DEFAULT 'pending',
    observed_condition          varchar(20),
    location                    varchar(255),
    remarks                     text,
    checked_by                  bigint REFERENCES users (id) ON DELETE SET NULL,
    checked_at                  timestamp(0),
    created_at                  timestamp(0),
    updated_at                  timestamp(0),
    CONSTRAINT physical_count_properties_result_check CHECK (result IN ('pending', 'found', 'missing')),
    CONSTRAINT physical_count_properties_condition_check
        CHECK (observed_condition IS NULL OR observed_condition IN ('excellent', 'good', 'fair', 'poor', 'unserviceable')),
    CONSTRAINT physical_count_properties_tag_unique UNIQUE (physical_inventory_count_id, tag_number)
);
CREATE INDEX physical_count_properties_property_idx ON physical_count_properties (property_id);
