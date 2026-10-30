-- Aligns procurement with the IRR of RA 12009: the ground and invitations of a Negotiated Procurement
-- (Section 35), the minimum quality score of MEARB (Section 61), and on what ground a common-use line
-- left PS-DBM (Section 40.2). "Unsolicited Offer" takes its name in the Act (Section 26). Shopping, which
-- the Act dropped, stays on past records; new lines and RFQs use Small Value Procurement instead.
ALTER TABLE rfqs ADD COLUMN negotiated_ground varchar(40);
ALTER TABLE rfqs ADD COLUMN invited_suppliers text;
ALTER TABLE biddings ADD COLUMN min_quality_score numeric(5,2);
ALTER TABLE ps_order_items ADD COLUMN release_ground varchar(30)
    CHECK (release_ground IS NULL OR release_ground IN ('zero_inventory', 'specs_insufficient', 'insufficient_stock', 'bac_resolution'));

UPDATE app_items SET procurement_mode = 'Unsolicited Offer with Bid Matching' WHERE procurement_mode = 'Unsolicited Offer';
UPDATE ppmp_items SET procurement_mode = 'Unsolicited Offer with Bid Matching' WHERE procurement_mode = 'Unsolicited Offer';
UPDATE purchase_request_items SET procurement_mode = 'Unsolicited Offer with Bid Matching' WHERE procurement_mode = 'Unsolicited Offer';
UPDATE rfqs SET procurement_method = 'Unsolicited Offer with Bid Matching' WHERE procurement_method = 'Unsolicited Offer';
