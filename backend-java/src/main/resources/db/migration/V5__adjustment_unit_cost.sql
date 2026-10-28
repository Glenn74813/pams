-- An approved increase now becomes a real stock lot (so lots, on-hand and weighted average cost stay
-- in step); it needs a unit cost. Left empty, the item's weighted average cost is used.
ALTER TABLE stock_adjustments ADD COLUMN unit_cost numeric(15, 2);
