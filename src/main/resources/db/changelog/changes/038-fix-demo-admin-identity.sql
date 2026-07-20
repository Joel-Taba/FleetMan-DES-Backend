--liquibase formatted sql
--changeset fleet-users:fix-demo-admin-identity-v1
-- Restaure l'identité locale du compte admin démo si écrasée par le Kernel (username = UUID).

UPDATE fleet.users
SET
  username = 'adminfleet',
  first_name = COALESCE(NULLIF(TRIM(first_name), ''), 'Marie'),
  last_name = COALESCE(NULLIF(TRIM(last_name), ''), 'Admin')
WHERE email = 'admin@fleetman.cm'
  AND (
    username ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    OR first_name IS NULL OR TRIM(first_name) = ''
    OR last_name IS NULL OR TRIM(last_name) = ''
  );
