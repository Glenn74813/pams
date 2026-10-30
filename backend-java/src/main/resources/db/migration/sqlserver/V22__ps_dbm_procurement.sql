-- Same tables as PostgreSQL V22: the PS-DBM price catalogue and Agency Procurement Requests (APR).
CREATE TABLE [ps_catalogue_items] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [code] nvarchar(50) NOT NULL,
    [description] nvarchar(500) NOT NULL,
    [unit] nvarchar(50) NOT NULL,
    [unit_price] decimal(15,2) NOT NULL CONSTRAINT [ps_catalogue_items_unit_price_check] CHECK ([unit_price] >= 0),
    [item_id] bigint,
    [is_active] bit NOT NULL CONSTRAINT [df_ps_catalogue_items_is_active] DEFAULT 1,
    [price_as_of] date,
    [created_at] datetime2(0),
    [updated_at] datetime2(0),
    CONSTRAINT [ps_catalogue_items_pkey] PRIMARY KEY ([id]),
    CONSTRAINT [ps_catalogue_items_item_id_foreign] FOREIGN KEY ([item_id]) REFERENCES [items] ([id])
);
CREATE UNIQUE INDEX [ps_catalogue_items_code_unique] ON [ps_catalogue_items] ([code]);

CREATE TABLE [ps_orders] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [apr_number] nvarchar(50) NOT NULL,
    [purchase_request_id] bigint NOT NULL,
    [status] nvarchar(20) NOT NULL CONSTRAINT [df_ps_orders_status] DEFAULT N'draft',
    [vs_reference] nvarchar(100),
    [submitted_date] date,
    [payment_mode] nvarchar(30),
    [payment_reference] nvarchar(100),
    [payment_date] date,
    [amount_paid] decimal(15,2),
    [dr_number] nvarchar(100),
    [dr_date] date,
    [delivery_mode] nvarchar(20),
    [purchase_order_id] bigint,
    [remarks] nvarchar(max),
    [prepared_by] bigint,
    [total_amount] decimal(15,2) NOT NULL CONSTRAINT [df_ps_orders_total_amount] DEFAULT 0,
    [created_at] datetime2(0),
    [updated_at] datetime2(0),
    [deleted_at] datetime2(0),
    CONSTRAINT [ps_orders_pkey] PRIMARY KEY ([id]),
    CONSTRAINT [ps_orders_status_check] CHECK ([status] IN (N'draft', N'submitted', N'paid', N'delivered', N'cancelled')),
    CONSTRAINT [ps_orders_payment_mode_check] CHECK ([payment_mode] IN (N'cash', N'lddap_ada', N'check', N'unutilized_funds')),
    CONSTRAINT [ps_orders_delivery_mode_check] CHECK ([delivery_mode] IN (N'pickup', N'delivery')),
    CONSTRAINT [ps_orders_purchase_request_id_foreign] FOREIGN KEY ([purchase_request_id]) REFERENCES [purchase_requests] ([id]),
    CONSTRAINT [ps_orders_purchase_order_id_foreign] FOREIGN KEY ([purchase_order_id]) REFERENCES [purchase_orders] ([id]),
    CONSTRAINT [ps_orders_prepared_by_foreign] FOREIGN KEY ([prepared_by]) REFERENCES [users] ([id])
);
CREATE UNIQUE INDEX [ps_orders_apr_number_unique] ON [ps_orders] ([apr_number]);
CREATE INDEX [ps_orders_purchase_request_id_index] ON [ps_orders] ([purchase_request_id]);

CREATE TABLE [ps_order_items] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [ps_order_id] bigint NOT NULL,
    [purchase_request_item_id] bigint NOT NULL,
    [ps_catalogue_item_id] bigint,
    [description] nvarchar(500) NOT NULL,
    [unit] nvarchar(50),
    [quantity] int NOT NULL CONSTRAINT [ps_order_items_quantity_check] CHECK ([quantity] > 0),
    [unit_price] decimal(15,2) NOT NULL,
    [amount] decimal(15,2) NOT NULL,
    [availability] nvarchar(20) NOT NULL CONSTRAINT [df_ps_order_items_availability] DEFAULT N'available',
    [cnas_number] nvarchar(100),
    [cnas_date] date,
    [created_at] datetime2(0),
    [updated_at] datetime2(0),
    CONSTRAINT [ps_order_items_pkey] PRIMARY KEY ([id]),
    CONSTRAINT [ps_order_items_availability_check] CHECK ([availability] IN (N'available', N'not_available')),
    CONSTRAINT [ps_order_items_ps_order_id_foreign] FOREIGN KEY ([ps_order_id]) REFERENCES [ps_orders] ([id]),
    CONSTRAINT [ps_order_items_pr_item_foreign] FOREIGN KEY ([purchase_request_item_id]) REFERENCES [purchase_request_items] ([id]),
    CONSTRAINT [ps_order_items_catalogue_foreign] FOREIGN KEY ([ps_catalogue_item_id]) REFERENCES [ps_catalogue_items] ([id])
);
CREATE INDEX [ps_order_items_order_index] ON [ps_order_items] ([ps_order_id]);
CREATE INDEX [ps_order_items_pr_item_index] ON [ps_order_items] ([purchase_request_item_id]);

ALTER TABLE [purchase_orders] ADD [ps_order_id] bigint;
ALTER TABLE [purchase_orders] ADD CONSTRAINT [purchase_orders_ps_order_id_foreign] FOREIGN KEY ([ps_order_id]) REFERENCES [ps_orders] ([id]);
