-- An RFQ award rests on the BAC resolution recommending it (as biddings already record).
ALTER TABLE rfqs ADD COLUMN bac_resolution_id bigint REFERENCES bac_resolutions (id) ON DELETE SET NULL;
