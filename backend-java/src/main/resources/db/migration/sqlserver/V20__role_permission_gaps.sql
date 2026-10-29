-- Same grants as PostgreSQL V20: stock adjustments, Monitoring and gate-pass editing for the roles
-- that do that work. Permissions a role already has are kept once; custom roles are not touched.
DECLARE @grants TABLE (role_name nvarchar(255), perm nvarchar(255));
INSERT INTO @grants VALUES
    (N'Supply Officer', N'create_adjustments'), (N'Supply Officer', N'view_monitoring'),
    (N'Warehouseman', N'create_adjustments'), (N'Warehouseman', N'view_monitoring'),
    (N'Admin', N'approve_adjustments'), (N'Admin', N'view_monitoring'), (N'Admin', N'view_gate_pass'), (N'Admin', N'edit_gate_pass'),
    (N'Property Officer', N'edit_gate_pass'),
    (N'Auditor', N'view_monitoring');

UPDATE r SET permissions = (
    SELECT N'[' + STRING_AGG(N'"' + STRING_ESCAPE(p.perm, 'json') + N'"', N', ') WITHIN GROUP (ORDER BY p.perm) + N']'
    FROM (SELECT j.value AS perm FROM OPENJSON(r.permissions) j
          UNION SELECT g.perm FROM @grants g WHERE g.role_name = r.name) p
), updated_at = SYSDATETIME()
FROM roles r
WHERE r.is_system = 1 AND r.name IN (SELECT role_name FROM @grants);
