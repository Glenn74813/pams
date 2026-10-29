-- Assets returned to the property office have no holder.
ALTER TABLE properties ALTER COLUMN assigned_to DROP NOT NULL;

-- The PAR (PPE) or ICS (semi-expendable) that put the asset under its current holder.
ALTER TABLE properties ADD COLUMN accountability_number varchar(50);

-- Transfers (PTR) and returns (PRS) share one history. A return has no "to" person;
-- a re-issue of a returned asset has no "from" person.
ALTER TABLE property_transfers ALTER COLUMN from_user DROP NOT NULL;
ALTER TABLE property_transfers ALTER COLUMN to_user DROP NOT NULL;
ALTER TABLE property_transfers ADD COLUMN transfer_type varchar(20) NOT NULL DEFAULT 'transfer';
ALTER TABLE property_transfers ADD CONSTRAINT property_transfers_type_check
    CHECK (transfer_type IN ('transfer', 'return', 'reissue'));
CREATE UNIQUE INDEX property_transfers_reference_number_unique
    ON property_transfers (reference_number) WHERE reference_number IS NOT NULL;

-- TOR 9.5: modes of disposal mandated by COA, plus what the asset was worth when it went.
ALTER TABLE disposal_requests DROP CONSTRAINT IF EXISTS disposal_requests_disposal_type_check;
ALTER TABLE disposal_requests ADD CONSTRAINT disposal_requests_disposal_type_check
    CHECK (disposal_type IN ('condemnation', 'destruction', 'donation', 'sale', 'public_auction',
                             'negotiated_sale', 'barter', 'transfer'));
ALTER TABLE disposal_requests ADD COLUMN book_value numeric(15, 2);
ALTER TABLE disposal_requests ADD COLUMN proceeds numeric(15, 2);

-- One depreciation posting per asset per month, so a monthly run can be repeated safely.
ALTER TABLE property_depreciation_logs ADD COLUMN period varchar(7);
CREATE UNIQUE INDEX property_depreciation_logs_property_period_unique
    ON property_depreciation_logs (property_id, period) WHERE period IS NOT NULL;
