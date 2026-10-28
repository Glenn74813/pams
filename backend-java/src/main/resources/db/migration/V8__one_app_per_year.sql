-- One Annual Procurement Plan per fiscal year. Consolidating again adds newly approved PPMPs to
-- the year's APP instead of creating a second, overlapping plan.
CREATE UNIQUE INDEX apps_fiscal_year_live_unique ON apps (fiscal_year) WHERE deleted_at IS NULL;

-- An APP line comes from at most one PPMP line, so reconsolidation can tell what is already in.
ALTER TABLE app_items ADD COLUMN ppmp_item_id bigint REFERENCES ppmp_items (id) ON DELETE SET NULL;
CREATE UNIQUE INDEX app_items_ppmp_item_unique ON app_items (ppmp_item_id) WHERE ppmp_item_id IS NOT NULL;
