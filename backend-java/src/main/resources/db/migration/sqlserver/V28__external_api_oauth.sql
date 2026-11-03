-- Same as PostgreSQL V28: OAuth 2.0 client credentials for external APIs.
ALTER TABLE [external_apis] ADD [token_url] nvarchar(500) NULL, [client_id] nvarchar(200) NULL;
ALTER TABLE [external_apis] DROP CONSTRAINT [external_apis_auth_type_check];
ALTER TABLE [external_apis] ADD CONSTRAINT [external_apis_auth_type_check] CHECK ([auth_type] IN (N'none', N'bearer', N'header', N'basic', N'oauth'));
