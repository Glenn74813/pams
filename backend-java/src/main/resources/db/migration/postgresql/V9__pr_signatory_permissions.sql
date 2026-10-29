-- The purchase request chain is signed with pr_approve_* permissions, but no system role carried
-- them, so only a Super Admin could move a PR forward. Give each step to its natural signatory,
-- and let End Users raise purchase requests. Permissions a role already has are kept once.
UPDATE roles r SET permissions = (
    SELECT json_agg(DISTINCT p ORDER BY p) FROM (
        SELECT json_array_elements_text(r.permissions) AS p
        UNION SELECT unnest(g.extra)
    ) all_perms
), updated_at = now()
FROM (VALUES
    ('Department Head', ARRAY['pr_approve_department']),
    ('Budget Officer',  ARRAY['pr_approve_budget']),
    ('Accountant',      ARRAY['pr_approve_accounting']),
    ('BAC Secretariat', ARRAY['pr_approve_bac']),
    ('End User',        ARRAY['create_procurement'])
) AS g(name, extra)
WHERE r.name = g.name AND r.is_system;
