-- Same changes as PostgreSQL V26: eligibility documents by mode (GPPB Circular 06-2026), failed Limited Source Bidding
-- and Competitive Dialogue (IRR 64.1), sealed MEARB and MARB prices (IRR 58), Unsolicited Offer with Bid Matching (IRR 30).
ALTER TABLE [suppliers] ADD [supplier_kind] nvarchar(20) NOT NULL CONSTRAINT [suppliers_supplier_kind_default] DEFAULT N'business';
ALTER TABLE [rfqs] ADD [eligibility_revised] bit NOT NULL CONSTRAINT [rfqs_eligibility_revised_default] DEFAULT 0;
ALTER TABLE [rfqs] ADD [documents_stage] nvarchar(20) NULL;
ALTER TABLE [rfqs] ADD [failure_reason] nvarchar(max) NULL;
ALTER TABLE [biddings] ADD [financial_opened_at] datetime2(0) NULL;
GO
UPDATE [biddings] SET [financial_opened_at] = [updated_at]
WHERE [evaluation_method] IN (N'mearb', N'marb')
  AND EXISTS (SELECT 1 FROM [bidding_bidders] x WHERE x.[bidding_id] = [biddings].[id] AND x.[bid_amount] IS NOT NULL);
GO
CREATE TABLE [unsolicited_offers] (
    [id] bigint IDENTITY(1,1) NOT NULL PRIMARY KEY,
    [offer_number] nvarchar(40) NOT NULL CONSTRAINT [unsolicited_offers_number_unique] UNIQUE,
    [supplier_id] bigint NOT NULL CONSTRAINT [unsolicited_offers_supplier_fk] REFERENCES [suppliers] ([id]),
    [title] nvarchar(255) NOT NULL,
    [concept] nvarchar(max) NOT NULL,
    [category] nvarchar(30) NOT NULL,
    [proposed_amount] decimal(15, 2) NOT NULL,
    [received_date] date NOT NULL,
    [status] nvarchar(30) NOT NULL CONSTRAINT [unsolicited_offers_status_default] DEFAULT N'received',
    [notice_date] date NULL,
    [documents_date] date NULL,
    [new_concept_basis] nvarchar(30) NULL,
    [evaluation_extension_days] int NULL,
    [evaluation_extension_reason] nvarchar(max) NULL,
    [evaluation_date] date NULL,
    [negotiation_start] date NULL,
    [negotiation_end] date NULL,
    [negotiated_amount] decimal(15, 2) NULL,
    [negotiated_terms] nvarchar(max) NULL,
    [certification_number] nvarchar(60) NULL,
    [certification_date] date NULL,
    [bid_security_form] nvarchar(40) NULL,
    [bid_security_amount] decimal(15, 2) NULL,
    [bid_security_date] date NULL,
    [bidding_id] bigint NULL CONSTRAINT [unsolicited_offers_bidding_fk] REFERENCES [biddings] ([id]),
    [lccrb_date] date NULL,
    [lccrb_supplier_id] bigint NULL CONSTRAINT [unsolicited_offers_lccrb_supplier_fk] REFERENCES [suppliers] ([id]),
    [lccrb_amount] decimal(15, 2) NULL,
    [match_result] nvarchar(20) NULL,
    [match_amount] decimal(15, 2) NULL,
    [match_date] date NULL,
    [closing_reason] nvarchar(max) NULL,
    [closed_date] date NULL,
    [late_withdrawal] bit NOT NULL CONSTRAINT [unsolicited_offers_late_default] DEFAULT 0,
    [created_by] bigint NULL,
    [created_at] datetime2(0) NULL,
    [updated_at] datetime2(0) NULL,
    [deleted_at] datetime2(0) NULL,
    CONSTRAINT [unsolicited_offers_category_check] CHECK ([category] IN (N'Goods', N'Consulting Services'))
);
CREATE INDEX [unsolicited_offers_status_index] ON [unsolicited_offers] ([status]);
CREATE INDEX [unsolicited_offers_supplier_index] ON [unsolicited_offers] ([supplier_id]);
