--liquibase formatted sql
--changeset fleet-business:trip-time-slots-v1 splitStatements:true

-- Disponibilité des véhicules et chauffeurs par CRÉNEAU HORAIRE (et non plus
-- « un seul trajet non terminé à la fois »).

-- 1. Marqueur : le trajet a lui-même posé la liaison chauffeur ↔ véhicule à son
--    départ. Seule une liaison posée par le trajet est défaite à sa clôture ; une
--    affectation permanente préexistante n'est plus effacée par un trajet.
ALTER TABLE fleet.trips
    ADD COLUMN IF NOT EXISTS driver_vehicle_linked BOOLEAN NOT NULL DEFAULT FALSE;

-- Trajets en cours liés par l'ancien code : ils seront déliés au retour, comme avant.
UPDATE fleet.trips t
SET driver_vehicle_linked = TRUE
WHERE t.status IN ('DEPARTED', 'RETURNING')
  AND EXISTS (
      SELECT 1 FROM fleet.drivers d
      WHERE d.user_id = t.driver_id AND d.assigned_vehicle_id = t.vehicle_id
  );

-- 2. Le véhicule n'est plus réservé (ON_TRIP) dès la planification, seulement au
--    départ réel : on libère les véhicules réservés par des trajets seulement planifiés.
UPDATE fleet.vehicles v
SET status = 'AVAILABLE'
WHERE v.status = 'ON_TRIP'
  AND NOT EXISTS (
      SELECT 1 FROM fleet.trips t
      WHERE t.vehicle_id = v.id
        AND t.status IN ('DEPARTED', 'RETURNING')
        AND t.deleted_at IS NULL
  );

-- 3. Kilométrage relevé à la dernière maintenance (seuils kilométriques préventifs).
ALTER TABLE fleet.maintenance_parameters
    ADD COLUMN IF NOT EXISTS last_maintenance_km NUMERIC(12, 2);
