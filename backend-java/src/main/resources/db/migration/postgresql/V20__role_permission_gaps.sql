-- Nobody but a Super Admin could make or approve a stock adjustment or open Monitoring, and gate
-- passes could not be edited by anyone. Give each to its natural holder. Permissions a role already
-- has are kept once; custom roles are not touched.
UPDATE roles r SET permissions = (
    SELECT json_agg(DISTINCT p ORDER BY p)::text FROM (
        SELECT json_array_elements_text(r.permissions::json) AS p
        UNION SELECT unnest(g.extra)
    ) all_perms
), updated_at = now()
FROM (VALUES
    ('Supply Officer',   ARRAY['create_adjustments', 'view_monitoring']),
    ('Warehouseman',     ARRAY['create_adjustments', 'view_monitoring']),
    ('Admin',            ARRAY['approve_adjustments', 'view_monitoring', 'view_gate_pass', 'edit_gate_pass']),
    ('Property Officer', ARRAY['edit_gate_pass']),
    ('Auditor',          ARRAY['view_monitoring'])
) AS g(name, extra)
WHERE r.name = g.name AND r.is_system;
