SET QUOTED_IDENTIFIER ON;
EXEC sp_MSforeachtable 'ALTER TABLE ? NOCHECK CONSTRAINT ALL; ALTER TABLE ? DISABLE TRIGGER ALL';
EXEC sp_MSforeachtable 'SET QUOTED_IDENTIFIER ON; IF PARSENAME(''?'',1) NOT IN (''flyway_schema_history'',''roles'',''departments'',''users'',''settings'',''system_settings'',''signatories'',''report_signatories'',''revoked_tokens'',''locations'') DELETE FROM ?';
SET QUOTED_IDENTIFIER ON;
DELETE FROM users WHERE email <> 'superadmin@inventory.com';
DELETE FROM roles WHERE is_system = 0;
EXEC sp_MSforeachtable 'ALTER TABLE ? WITH NOCHECK CHECK CONSTRAINT ALL; ALTER TABLE ? ENABLE TRIGGER ALL';
