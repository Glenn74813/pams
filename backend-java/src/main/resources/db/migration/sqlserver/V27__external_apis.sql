-- Same as PostgreSQL V27: other systems' APIs that PAMS reads and shows, read-only.
CREATE TABLE [external_apis] (
    [id] bigint IDENTITY(1,1) NOT NULL PRIMARY KEY,
    [name] nvarchar(120) NOT NULL CONSTRAINT [external_apis_name_unique] UNIQUE,
    [description] nvarchar(max) NULL,
    [category] nvarchar(30) NOT NULL CONSTRAINT [external_apis_category_default] DEFAULT N'payments',
    [base_url] nvarchar(500) NOT NULL,
    [path] nvarchar(500) NOT NULL CONSTRAINT [external_apis_path_default] DEFAULT N'',
    [auth_type] nvarchar(20) NOT NULL CONSTRAINT [external_apis_auth_default] DEFAULT N'none',
    [auth_header] nvarchar(100) NULL,
    [secret_cipher] nvarchar(max) NULL,
    [data_path] nvarchar(200) NULL,
    [columns] nvarchar(max) NULL,
    [context] nvarchar(20) NOT NULL CONSTRAINT [external_apis_context_default] DEFAULT N'none',
    [timeout_seconds] int NOT NULL CONSTRAINT [external_apis_timeout_default] DEFAULT 10,
    [is_active] bit NOT NULL CONSTRAINT [external_apis_active_default] DEFAULT 1,
    [last_status] nvarchar(300) NULL,
    [last_called_at] datetime2(0) NULL,
    [created_by] bigint NULL,
    [created_at] datetime2(0) NULL,
    [updated_at] datetime2(0) NULL,
    CONSTRAINT [external_apis_auth_type_check] CHECK ([auth_type] IN (N'none', N'bearer', N'header', N'basic')),
    CONSTRAINT [external_apis_context_check] CHECK ([context] IN (N'none', N'payable'))
);
