-- PO lines keep their unit and the award line they come from; a PO records who prepared it
-- (the one who approves it must be someone else) and why it was cancelled.
ALTER TABLE purchase_order_items ADD COLUMN unit varchar(50);
ALTER TABLE purchase_order_items ADD COLUMN rfq_item_id bigint REFERENCES rfq_items (id) ON DELETE SET NULL;
ALTER TABLE purchase_order_items ADD COLUMN bidding_item_id bigint REFERENCES bidding_items (id) ON DELETE SET NULL;
ALTER TABLE purchase_orders ADD COLUMN created_by bigint REFERENCES users (id) ON DELETE SET NULL;

-- One live purchase order per award.
CREATE UNIQUE INDEX purchase_orders_rfq_live_unique ON purchase_orders (rfq_id) WHERE rfq_id IS NOT NULL AND deleted_at IS NULL AND status <> 'cancelled';
CREATE UNIQUE INDEX purchase_orders_bidding_live_unique ON purchase_orders (bidding_id) WHERE bidding_id IS NOT NULL AND deleted_at IS NULL AND status <> 'cancelled';
