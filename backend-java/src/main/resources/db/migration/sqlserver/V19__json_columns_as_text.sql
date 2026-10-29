-- PostgreSQL V19 turns roles.permissions and bidding_bidders.eligibility_checklist from json into text;
-- on SQL Server they are nvarchar(max) from the V18 baseline already. Kept so both databases share version numbers.
SELECT 1;
