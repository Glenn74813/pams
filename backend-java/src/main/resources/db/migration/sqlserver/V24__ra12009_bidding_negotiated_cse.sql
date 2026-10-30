-- Same changes as PostgreSQL V24: Negotiated Procurement ground and invitations, MEARB minimum quality
-- score, the ground a common-use line left PS-DBM, and the Act's name for Unsolicited Offer.
ALTER TABLE [rfqs] ADD [negotiated_ground] nvarchar(40) NULL;
ALTER TABLE [rfqs] ADD [invited_suppliers] nvarchar(max) NULL;
ALTER TABLE [biddings] ADD [min_quality_score] decimal(5,2) NULL;
ALTER TABLE [ps_order_items] ADD [release_ground] nvarchar(30) NULL;
GO
ALTER TABLE [ps_order_items] ADD CONSTRAINT [ps_order_items_release_ground_check]
    CHECK ([release_ground] IS NULL OR [release_ground] IN (N'zero_inventory', N'specs_insufficient', N'insufficient_stock', N'bac_resolution'));

UPDATE [app_items] SET [procurement_mode] = N'Unsolicited Offer with Bid Matching' WHERE [procurement_mode] = N'Unsolicited Offer';
UPDATE [ppmp_items] SET [procurement_mode] = N'Unsolicited Offer with Bid Matching' WHERE [procurement_mode] = N'Unsolicited Offer';
UPDATE [purchase_request_items] SET [procurement_mode] = N'Unsolicited Offer with Bid Matching' WHERE [procurement_mode] = N'Unsolicited Offer';
UPDATE [rfqs] SET [procurement_method] = N'Unsolicited Offer with Bid Matching' WHERE [procurement_method] = N'Unsolicited Offer';
