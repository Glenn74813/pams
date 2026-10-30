-- A gate pass returned as "lost" leaves the asset lost: still on its custodian's books until
-- relieved (RLSDDP, then turn-in and disposal), and blocked from transfers and gate passes.
ALTER TABLE properties DROP CONSTRAINT IF EXISTS properties_status_check;
ALTER TABLE properties ADD CONSTRAINT properties_status_check
    CHECK (status IN ('assigned', 'available', 'under_repair', 'retired', 'disposed', 'lost'));

-- Accounts can be deactivated instead of deleted, and an administrator can clear a lockout.
ALTER TABLE users ADD COLUMN is_active boolean NOT NULL DEFAULT true;
ALTER TABLE users ADD COLUMN lockout_cleared_at timestamp(0) without time zone;

-- Issue slips print the cost at the time of issue, not today's average.
ALTER TABLE request_items ADD COLUMN unit_cost numeric(15,2);

-- The approver's remarks on a disposal approval or rejection.
ALTER TABLE disposal_requests ADD COLUMN approval_remarks text;

-- Audit rows are sealed with an HMAC when written and can only be appended. The one change allowed
-- is clearing user_id when a user is deleted (the name stays in user_name).
ALTER TABLE audit_trails ADD COLUMN seal varchar(64);

CREATE FUNCTION audit_trails_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND NEW.user_id IS NULL
       AND (NEW.id, NEW.user_name, NEW.user_role, NEW.action, NEW.auditable_type, NEW.auditable_id, NEW.description,
            NEW.old_values::text, NEW.new_values::text, NEW.ip_address, NEW.user_agent, NEW.url, NEW.method, NEW.created_at, NEW.updated_at, NEW.seal)
           IS NOT DISTINCT FROM
           (OLD.id, OLD.user_name, OLD.user_role, OLD.action, OLD.auditable_type, OLD.auditable_id, OLD.description,
            OLD.old_values::text, OLD.new_values::text, OLD.ip_address, OLD.user_agent, OLD.url, OLD.method, OLD.created_at, OLD.updated_at, OLD.seal) THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'The audit trail cannot be changed or deleted.';
END
$$;

CREATE TRIGGER audit_trails_append_only BEFORE UPDATE OR DELETE ON audit_trails
    FOR EACH ROW EXECUTE FUNCTION audit_trails_append_only();
CREATE TRIGGER audit_trails_no_truncate BEFORE TRUNCATE ON audit_trails
    FOR EACH STATEMENT EXECUTE FUNCTION audit_trails_append_only();
