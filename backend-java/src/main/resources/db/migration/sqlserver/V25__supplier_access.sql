-- Same grant as PostgreSQL V25: view access to suppliers for the roles that work with them.
UPDATE r SET permissions = (
    SELECT N'[' + STRING_AGG(N'"' + STRING_ESCAPE(p.perm, 'json') + N'"', N', ') WITHIN GROUP (ORDER BY p.perm) + N']'
    FROM (SELECT j.value AS perm FROM OPENJSON(r.permissions) j UNION SELECT N'view_suppliers') p
), updated_at = SYSDATETIME()
FROM roles r
WHERE r.name IN (N'BAC Chairperson', N'BAC Secretariat', N'Supply Officer', N'Warehouseman', N'Property Officer', N'Accountant', N'Auditor');
