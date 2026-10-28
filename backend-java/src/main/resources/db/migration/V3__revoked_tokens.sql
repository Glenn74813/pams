-- Tokens invalidated by logout or refresh. Rows can be purged once expires_at has passed.
CREATE TABLE revoked_tokens (
    jti        varchar(64) PRIMARY KEY,
    user_id    bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at timestamp(0) without time zone NOT NULL,
    revoked_at timestamp(0) without time zone NOT NULL
);

CREATE INDEX revoked_tokens_expires_at_index ON revoked_tokens (expires_at);
