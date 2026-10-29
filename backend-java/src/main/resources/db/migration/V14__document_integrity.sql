-- SHA-256 of each stored file, so a download can be checked against what was uploaded and an
-- identical re-upload is refused instead of becoming a new version.
ALTER TABLE document_versions ADD COLUMN checksum varchar(64);
CREATE INDEX documents_status_index ON documents (status);
