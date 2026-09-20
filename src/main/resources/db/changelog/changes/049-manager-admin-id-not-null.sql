--liquibase formatted sql
--changeset fleet-admin:manager-created-by-admin-not-null-v1 splitStatements:true

-- created_by_admin_id doit pointer vers un FLEET_ADMIN (platform_admins), jamais NULL.

INSERT INTO fleet.platform_admins (user_id)
SELECT u.id FROM fleet.users u
WHERE u.email IN (
    'admin@fleetman.cm', 'admin2@fleetman.cm', 'admin3@fleetman.cm',
    'admin4@fleetman.cm', 'admin5@fleetman.cm', 'joeltaba4@gmail.com'
)
ON CONFLICT (user_id) DO NOTHING;

ALTER TABLE fleet.fleet_managers
    DROP CONSTRAINT IF EXISTS fleet_managers_created_by_admin_id_fkey;

ALTER TABLE fleet.fleet_managers
    DROP CONSTRAINT IF EXISTS fk_manager_created_by_admin;

UPDATE fleet.fleet_managers fm
SET created_by_admin_id = COALESCE(
    (
        SELECT pa.user_id FROM fleet.platform_admins pa
        WHERE pa.user_id = fm.created_by_admin_id
        LIMIT 1
    ),
    (
        SELECT pa.user_id FROM fleet.platform_admins pa
        JOIN fleet.users u ON u.id = pa.user_id
        WHERE u.email = 'admin@fleetman.cm'
        LIMIT 1
    ),
    (SELECT pa.user_id FROM fleet.platform_admins pa LIMIT 1)
)
WHERE fm.created_by_admin_id IS NULL
   OR NOT EXISTS (
        SELECT 1 FROM fleet.platform_admins pa WHERE pa.user_id = fm.created_by_admin_id
   );

ALTER TABLE fleet.fleet_managers
    ALTER COLUMN created_by_admin_id SET NOT NULL;

ALTER TABLE fleet.fleet_managers
    ADD CONSTRAINT fk_manager_created_by_admin
    FOREIGN KEY (created_by_admin_id) REFERENCES fleet.platform_admins(user_id)
    ON DELETE RESTRICT;
