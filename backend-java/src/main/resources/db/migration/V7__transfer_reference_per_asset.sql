-- One PAR / ICS / PTR can cover several assets (a batch registered or moved together), so a
-- document number is unique per asset, not across the whole table.
DROP INDEX IF EXISTS property_transfers_reference_number_unique;
CREATE UNIQUE INDEX property_transfers_property_reference_unique
    ON property_transfers (property_id, reference_number) WHERE reference_number IS NOT NULL;
