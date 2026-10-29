-- Tables only the retired Laravel app used. Its migration log, the hand-entered procurement
-- milestones (the bidding timeline is now computed from the recorded dates), the unused electronic
-- signature records, and the disposal reminders (alerts now go through notifications).
DROP TABLE IF EXISTS disposal_notifications;
DROP TABLE IF EXISTS electronic_signatures;
DROP TABLE IF EXISTS procurement_milestones;
DROP TABLE IF EXISTS migrations;
