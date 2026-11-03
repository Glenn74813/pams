-- OAuth 2.0 client credentials for external APIs: PAMS asks the token address for a short-lived access token with
-- the client id and secret (secret_cipher), and sends it as a bearer token.
ALTER TABLE external_apis ADD COLUMN token_url varchar(500);
ALTER TABLE external_apis ADD COLUMN client_id varchar(200);
ALTER TABLE external_apis DROP CONSTRAINT external_apis_auth_type_check;
ALTER TABLE external_apis ADD CONSTRAINT external_apis_auth_type_check CHECK (auth_type IN ('none', 'bearer', 'header', 'basic', 'oauth'));
