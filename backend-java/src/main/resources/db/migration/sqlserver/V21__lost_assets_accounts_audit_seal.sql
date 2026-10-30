-- Same changes as PostgreSQL V21: lost assets, account deactivation and lockout clearing, cost at
-- issue, disposal approval remarks, and a sealed, append-only audit trail.
ALTER TABLE [properties] DROP CONSTRAINT [properties_status_check];
ALTER TABLE [properties] ADD CONSTRAINT [properties_status_check]
    CHECK ([status] IN (N'assigned', N'available', N'under_repair', N'retired', N'disposed', N'lost'));

ALTER TABLE [users] ADD [is_active] bit NOT NULL CONSTRAINT [df_users_is_active] DEFAULT 1;
ALTER TABLE [users] ADD [lockout_cleared_at] datetime2(0);

ALTER TABLE [request_items] ADD [unit_cost] decimal(15,2);

ALTER TABLE [disposal_requests] ADD [approval_remarks] nvarchar(max);

ALTER TABLE [audit_trails] ADD [seal] nvarchar(64);
GO

-- Deleting a user clears user_id here (the users delete trigger); every other change is refused.
CREATE TRIGGER [audit_trails_append_only] ON [audit_trails] INSTEAD OF UPDATE, DELETE AS
BEGIN
    SET NOCOUNT ON;
    -- A statement that matched no rows (e.g. deleting a user with no audit entries) changes nothing.
    IF NOT EXISTS (SELECT 1 FROM deleted) RETURN;
    IF NOT EXISTS (SELECT 1 FROM inserted)
        THROW 51000, N'The audit trail cannot be changed or deleted.', 1;
    IF EXISTS (
        SELECT 1 FROM inserted i JOIN deleted d ON d.[id] = i.[id]
        WHERE i.[user_id] IS NOT NULL
           OR EXISTS (SELECT i.[user_name], i.[user_role], i.[action], i.[auditable_type], i.[auditable_id], CAST(i.[description] AS nvarchar(max)),
                             CAST(i.[old_values] AS nvarchar(max)), CAST(i.[new_values] AS nvarchar(max)), i.[ip_address], i.[user_agent], i.[url],
                             i.[method], i.[created_at], i.[updated_at], i.[seal]
                      EXCEPT
                      SELECT d.[user_name], d.[user_role], d.[action], d.[auditable_type], d.[auditable_id], CAST(d.[description] AS nvarchar(max)),
                             CAST(d.[old_values] AS nvarchar(max)), CAST(d.[new_values] AS nvarchar(max)), d.[ip_address], d.[user_agent], d.[url],
                             d.[method], d.[created_at], d.[updated_at], d.[seal]))
        THROW 51000, N'The audit trail cannot be changed or deleted.', 1;
    UPDATE a SET [user_id] = NULL FROM [audit_trails] a JOIN inserted i ON i.[id] = a.[id];
END
