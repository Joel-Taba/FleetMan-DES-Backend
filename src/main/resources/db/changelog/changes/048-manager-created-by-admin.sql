--liquibase formatted sql
--changeset fleet-admin:manager-created-by-admin-v1 splitStatements:true

-- Un manager appartient à l'organisation de l'admin qui l'a créé (1 admin = 1 org).
ALTER TABLE fleet.fleet_managers
    ADD COLUMN IF NOT EXISTS created_by_admin_id UUID REFERENCES fleet.users(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_fleet_managers_created_by_admin
    ON fleet.fleet_managers(created_by_admin_id);

-- Rattrapage démo : managers déjà en base, rattachés à l'admin principal.
UPDATE fleet.fleet_managers fm
SET created_by_admin_id = (
    SELECT u.id FROM fleet.users u
    WHERE u.email = 'admin@fleetman.cm'
    LIMIT 1
)
WHERE fm.created_by_admin_id IS NULL
  AND EXISTS (SELECT 1 FROM fleet.users u WHERE u.email = 'admin@fleetman.cm');
