-- Authentication moved from custom X-API-Key lookups to Keycloak-issued OAuth2 JWTs
-- (see SecurityConfig). The api_keys table is no longer read or written by this service.
DROP TABLE IF EXISTS api_keys;
