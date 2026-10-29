-- TOR Technical X / C 13.1: bulk imports of master data. Each upload is validated (dry run) into a
-- batch, then committed or aborted. The per-row exceptions (duplicate, incomplete, invalid rows and
-- any failure that aborted the commit) are kept as JSON so the exception report can be re-opened.
-- payload holds the uploaded CSV between validation and commit and is cleared afterwards.
CREATE TABLE import_batches (
    id               bigserial PRIMARY KEY,
    entity           varchar(30)  NOT NULL,
    file_name        varchar(255) NOT NULL,
    status           varchar(20)  NOT NULL,
    total_rows       integer      NOT NULL DEFAULT 0,
    ok_rows          integer      NOT NULL DEFAULT 0,
    duplicate_rows   integer      NOT NULL DEFAULT 0,
    incomplete_rows  integer      NOT NULL DEFAULT 0,
    invalid_rows     integer      NOT NULL DEFAULT 0,
    imported_rows    integer      NOT NULL DEFAULT 0,
    options          text,
    exceptions       text,
    error_message    text,
    payload          text,
    created_by       bigint REFERENCES users (id) ON DELETE SET NULL,
    created_by_name  varchar(255),
    committed_by     bigint REFERENCES users (id) ON DELETE SET NULL,
    committed_at     timestamp(0),
    aborted_at       timestamp(0),
    created_at       timestamp(0),
    updated_at       timestamp(0),
    CONSTRAINT import_batches_status_check CHECK (status IN ('validated', 'committed', 'aborted')),
    CONSTRAINT import_batches_entity_check CHECK (entity IN ('items', 'suppliers', 'departments', 'users'))
);

CREATE INDEX import_batches_created_at_index ON import_batches (created_at);
