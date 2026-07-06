-- Patch idempotent : lie les flottes demo à l'organisation Kernel FleetMan Cameroun
UPDATE fleet.fleets
SET kernel_organization_id = '5e69f5c5-1f03-41cb-a4a3-d59188f73323'
WHERE kernel_organization_id IS NULL;
