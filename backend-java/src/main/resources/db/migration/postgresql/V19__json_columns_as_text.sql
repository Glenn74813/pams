-- Role permissions and the bidder eligibility checklist are read and written by the entities as JSON
-- text, the one representation PostgreSQL and SQL Server share (SQL Server keeps them in nvarchar(max)).
ALTER TABLE roles ALTER COLUMN permissions TYPE text USING permissions::text;
ALTER TABLE bidding_bidders ALTER COLUMN eligibility_checklist TYPE text USING eligibility_checklist::text;
