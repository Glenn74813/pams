-- A PPMP line may name the catalogue item it buys, and its APP line keeps that link, so the items at or below their
-- reorder point can be turned into a purchase request drawn on the APP lines that plan them.
ALTER TABLE ppmp_items ADD COLUMN item_id bigint REFERENCES items(id);
CREATE INDEX ppmp_items_item_id_index ON ppmp_items (item_id);
ALTER TABLE app_items ADD COLUMN item_id bigint REFERENCES items(id);
CREATE INDEX app_items_item_id_index ON app_items (item_id);
