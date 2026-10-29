-- TOR General 6.7: who is using the system right now. Touched (at most once a minute) on every
-- authenticated request and cleared on sign-out.
ALTER TABLE users ADD COLUMN last_seen_at timestamp(0);

CREATE INDEX audit_trails_user_id_index ON audit_trails (user_id);
