-- Same tables as PostgreSQL V23: asset locations and photos, maintenance, clearance certificates,
-- document signing against trusted CAs, and the SSO subject on users.
CREATE TABLE [asset_locations] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [name] nvarchar(150) NOT NULL,
    [building] nvarchar(150),
    [floor] nvarchar(50),
    [room] nvarchar(100),
    [description] nvarchar(500),
    [is_active] bit NOT NULL CONSTRAINT [df_asset_locations_is_active] DEFAULT 1,
    [created_at] datetime2(0),
    [updated_at] datetime2(0),
    CONSTRAINT [asset_locations_pkey] PRIMARY KEY ([id])
);
CREATE UNIQUE INDEX [asset_locations_name_unique] ON [asset_locations] ([name]);

ALTER TABLE [properties] ADD [location_id] bigint NULL CONSTRAINT [properties_location_id_foreign] FOREIGN KEY REFERENCES [asset_locations] ([id]);
ALTER TABLE [properties] ADD [photo_path] nvarchar(255) NULL;
ALTER TABLE [items] ADD [photo_path] nvarchar(255) NULL;
GO
CREATE INDEX [properties_location_id_index] ON [properties] ([location_id]);

CREATE TABLE [property_location_moves] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [property_id] bigint NOT NULL,
    [from_location_id] bigint,
    [to_location_id] bigint,
    [moved_by] bigint,
    [remarks] nvarchar(500),
    [created_at] datetime2(0),
    CONSTRAINT [property_location_moves_pkey] PRIMARY KEY ([id]),
    CONSTRAINT [property_location_moves_property_id_foreign] FOREIGN KEY ([property_id]) REFERENCES [properties] ([id]),
    CONSTRAINT [property_location_moves_from_foreign] FOREIGN KEY ([from_location_id]) REFERENCES [asset_locations] ([id]),
    CONSTRAINT [property_location_moves_to_foreign] FOREIGN KEY ([to_location_id]) REFERENCES [asset_locations] ([id]),
    CONSTRAINT [property_location_moves_moved_by_foreign] FOREIGN KEY ([moved_by]) REFERENCES [users] ([id])
);
CREATE INDEX [property_location_moves_property_id_index] ON [property_location_moves] ([property_id]);

CREATE TABLE [property_maintenance] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [property_id] bigint NOT NULL,
    [maintenance_type] nvarchar(20) NOT NULL,
    [description] nvarchar(500) NOT NULL,
    [scheduled_date] date NOT NULL,
    [interval_months] int,
    [status] nvarchar(20) NOT NULL CONSTRAINT [df_property_maintenance_status] DEFAULT N'scheduled',
    [completed_date] date,
    [performed_by] nvarchar(255),
    [cost] decimal(15,2),
    [remarks] nvarchar(max),
    [created_by] bigint,
    [completed_by] bigint,
    [created_at] datetime2(0),
    [updated_at] datetime2(0),
    CONSTRAINT [property_maintenance_pkey] PRIMARY KEY ([id]),
    CONSTRAINT [property_maintenance_type_check] CHECK ([maintenance_type] IN (N'preventive', N'corrective', N'inspection', N'calibration')),
    CONSTRAINT [property_maintenance_interval_check] CHECK ([interval_months] IS NULL OR [interval_months] BETWEEN 1 AND 120),
    CONSTRAINT [property_maintenance_status_check] CHECK ([status] IN (N'scheduled', N'done', N'cancelled')),
    CONSTRAINT [property_maintenance_cost_check] CHECK ([cost] IS NULL OR [cost] >= 0),
    CONSTRAINT [property_maintenance_property_id_foreign] FOREIGN KEY ([property_id]) REFERENCES [properties] ([id]),
    CONSTRAINT [property_maintenance_created_by_foreign] FOREIGN KEY ([created_by]) REFERENCES [users] ([id]),
    CONSTRAINT [property_maintenance_completed_by_foreign] FOREIGN KEY ([completed_by]) REFERENCES [users] ([id])
);
CREATE INDEX [property_maintenance_property_id_index] ON [property_maintenance] ([property_id]);
CREATE INDEX [property_maintenance_status_date_index] ON [property_maintenance] ([status], [scheduled_date]);

CREATE TABLE [clearance_certificates] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [certificate_number] nvarchar(50),
    [user_id] bigint NOT NULL,
    [purpose] nvarchar(30) NOT NULL,
    [purpose_details] nvarchar(255),
    [effective_date] date,
    [status] nvarchar(20) NOT NULL CONSTRAINT [df_clearance_certificates_status] DEFAULT N'pending',
    [requested_by] bigint,
    [issued_by] bigint,
    [issued_at] datetime2(0),
    [assets_turned_over] int NOT NULL CONSTRAINT [df_clearance_certificates_assets] DEFAULT 0,
    [remarks] nvarchar(max),
    [created_at] datetime2(0),
    [updated_at] datetime2(0),
    CONSTRAINT [clearance_certificates_pkey] PRIMARY KEY ([id]),
    CONSTRAINT [clearance_certificates_purpose_check] CHECK ([purpose] IN (N'resignation', N'retirement', N'transfer', N'end_of_contract', N'leave', N'other')),
    CONSTRAINT [clearance_certificates_status_check] CHECK ([status] IN (N'pending', N'issued', N'cancelled')),
    CONSTRAINT [clearance_certificates_user_id_foreign] FOREIGN KEY ([user_id]) REFERENCES [users] ([id]),
    CONSTRAINT [clearance_certificates_requested_by_foreign] FOREIGN KEY ([requested_by]) REFERENCES [users] ([id]),
    CONSTRAINT [clearance_certificates_issued_by_foreign] FOREIGN KEY ([issued_by]) REFERENCES [users] ([id])
);
CREATE UNIQUE INDEX [clearance_certificates_number_unique] ON [clearance_certificates] ([certificate_number]) WHERE [certificate_number] IS NOT NULL;
CREATE INDEX [clearance_certificates_user_id_index] ON [clearance_certificates] ([user_id]);

CREATE TABLE [trusted_certificates] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [name] nvarchar(150) NOT NULL,
    [pem] nvarchar(max) NOT NULL,
    [subject] nvarchar(500) NOT NULL,
    [fingerprint] nvarchar(64) NOT NULL,
    [not_after] datetime2(0),
    [created_by] bigint,
    [created_at] datetime2(0),
    CONSTRAINT [trusted_certificates_pkey] PRIMARY KEY ([id]),
    CONSTRAINT [trusted_certificates_created_by_foreign] FOREIGN KEY ([created_by]) REFERENCES [users] ([id])
);
CREATE UNIQUE INDEX [trusted_certificates_fingerprint_unique] ON [trusted_certificates] ([fingerprint]);

CREATE TABLE [signing_requests] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [request_number] nvarchar(50) NOT NULL,
    [title] nvarchar(255) NOT NULL,
    [file_name] nvarchar(255) NOT NULL,
    [file_path] nvarchar(255) NOT NULL,
    [content_type] nvarchar(150),
    [file_sha256] nvarchar(64) NOT NULL,
    [file_size] bigint NOT NULL,
    [requested_by] bigint NOT NULL,
    [signer_id] bigint NOT NULL,
    [status] nvarchar(20) NOT NULL CONSTRAINT [df_signing_requests_status] DEFAULT N'pending',
    [platform_reference] nvarchar(255),
    [remarks] nvarchar(max),
    [signed_at] datetime2(0),
    [created_at] datetime2(0),
    [updated_at] datetime2(0),
    CONSTRAINT [signing_requests_pkey] PRIMARY KEY ([id]),
    CONSTRAINT [signing_requests_status_check] CHECK ([status] IN (N'pending', N'signed', N'declined', N'cancelled')),
    CONSTRAINT [signing_requests_requested_by_foreign] FOREIGN KEY ([requested_by]) REFERENCES [users] ([id]),
    CONSTRAINT [signing_requests_signer_id_foreign] FOREIGN KEY ([signer_id]) REFERENCES [users] ([id])
);
CREATE UNIQUE INDEX [signing_requests_number_unique] ON [signing_requests] ([request_number]);
CREATE INDEX [signing_requests_signer_status_index] ON [signing_requests] ([signer_id], [status]);
CREATE INDEX [signing_requests_sha256_index] ON [signing_requests] ([file_sha256]);

CREATE TABLE [document_signatures] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [signing_request_id] bigint NOT NULL,
    [signer_id] bigint NOT NULL,
    [file_sha256] nvarchar(64) NOT NULL,
    [algorithm] nvarchar(30) NOT NULL,
    [signature] nvarchar(max) NOT NULL,
    [certificate_pem] nvarchar(max) NOT NULL,
    [certificate_subject] nvarchar(500) NOT NULL,
    [certificate_issuer] nvarchar(500) NOT NULL,
    [certificate_serial] nvarchar(100) NOT NULL,
    [certificate_not_after] datetime2(0),
    [trusted_certificate_id] bigint,
    [source] nvarchar(20) NOT NULL,
    [signed_at] datetime2(0) NOT NULL,
    CONSTRAINT [document_signatures_pkey] PRIMARY KEY ([id]),
    CONSTRAINT [document_signatures_source_check] CHECK ([source] IN (N'in_app', N'platform')),
    CONSTRAINT [document_signatures_request_foreign] FOREIGN KEY ([signing_request_id]) REFERENCES [signing_requests] ([id]),
    CONSTRAINT [document_signatures_signer_id_foreign] FOREIGN KEY ([signer_id]) REFERENCES [users] ([id]),
    CONSTRAINT [document_signatures_trusted_foreign] FOREIGN KEY ([trusted_certificate_id]) REFERENCES [trusted_certificates] ([id])
);
CREATE INDEX [document_signatures_sha256_index] ON [document_signatures] ([file_sha256]);

ALTER TABLE [users] ADD [sso_subject] nvarchar(255) NULL;
GO
CREATE UNIQUE INDEX [users_sso_subject_unique] ON [users] ([sso_subject]) WHERE [sso_subject] IS NOT NULL;
