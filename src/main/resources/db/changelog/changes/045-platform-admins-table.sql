--liquibase formatted sql
--changeset fleet-super-admin:platform-admins-table-v1 splitStatements:true

-- Marqueur local "cet utilisateur est un FLEET_ADMIN". Nécessaire car
-- GET /api/users/admins du Kernel renvoie un tableau JSON brut que le client
-- FleetMan ne peut pas désérialiser (échec silencieux -> liste toujours vide),
-- ce qui empêchait tout nouvel administrateur créé d'apparaître dans la liste
-- "Administrateurs Système" (seuls les comptes de démo repli codés en dur
-- étaient visibles).
CREATE TABLE IF NOT EXISTS fleet.platform_admins (
    user_id    UUID PRIMARY KEY REFERENCES fleet.users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Rattrapage : les comptes admin de démo déjà présents en base.
INSERT INTO fleet.platform_admins (user_id)
SELECT id FROM fleet.users
WHERE email IN (
    'admin@fleetman.cm', 'admin2@fleetman.cm', 'admin3@fleetman.cm',
    'admin4@fleetman.cm', 'admin5@fleetman.cm'
)
ON CONFLICT (user_id) DO NOTHING;
