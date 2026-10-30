-- The supplier module was hidden from most roles that work with suppliers: the BAC checks their eligibility,
-- supply and property staff receive from them, and accounting and audit trace who was paid. Give those roles
-- view access; permissions a role already has are kept once, and custom roles are not touched.
UPDATE roles r SET permissions = (
    SELECT json_agg(DISTINCT p ORDER BY p)::text FROM (
        SELECT json_array_elements_text(r.permissions::json) AS p
        UNION SELECT 'view_suppliers'
    ) all_perms
), updated_at = now()
WHERE r.name IN ('BAC Chairperson', 'BAC Secretariat', 'Supply Officer', 'Warehouseman', 'Property Officer', 'Accountant', 'Auditor');
