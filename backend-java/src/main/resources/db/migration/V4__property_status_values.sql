-- The Laravel UI offered "in-use" and "retired" while the column only allowed
-- assigned/available/under_repair/disposed, so saving those statuses failed.
-- Allow "retired" (reports already print it); "in-use" is the same as "assigned" and is dropped from the UI.
ALTER TABLE properties DROP CONSTRAINT IF EXISTS properties_status_check;
ALTER TABLE properties ADD CONSTRAINT properties_status_check
    CHECK (status IN ('assigned', 'available', 'under_repair', 'retired', 'disposed'));
