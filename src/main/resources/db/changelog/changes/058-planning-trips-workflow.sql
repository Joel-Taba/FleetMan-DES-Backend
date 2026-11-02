--liquibase formatted sql

-- Programmes, affectations et trajets, alignés sur l'exploitation réelle :
--   * programme = roulement publié aux chauffeurs ; affectation = poste (chauffeur + véhicule
--     réservés sur un créneau) ; les trajets sont les missions faites pendant le poste ;
--   * l'affectation perd ce qui relève du trajet (lieux, km estimés, mission) : ses km viennent
--     de ses trajets ;
--   * trajet : destination obligatoire, arrivée à destination (livraison) puis retour à la base ;
--     un trajet planifié non parti 2 h après l'heure prévue est « manqué » ;
--   * tarification (coût de mission, type de tarif) retirée : elle relève du cœur G1 ; les
--     colonnes restent en base à titre d'archive ;
--   * compléments de mission rattachés au manifeste (ajout ou retrait de chargement) ;
--   * suppression des éléments morts : table routes (jamais utilisée), type de véhicule recopié
--     sur le trajet.

--changeset fleetman:058-trip-destination-arrival
ALTER TABLE fleet.trips
    ADD COLUMN IF NOT EXISTS destination_location VARCHAR(255),
    ADD COLUMN IF NOT EXISTS destination_lat      NUMERIC(10,7),
    ADD COLUMN IF NOT EXISTS destination_lng      NUMERIC(10,7),
    ADD COLUMN IF NOT EXISTS arrived_at           TIMESTAMP,
    ADD COLUMN IF NOT EXISTS arrival_location     VARCHAR(255),
    ADD COLUMN IF NOT EXISTS arrival_lat          NUMERIC(10,7),
    ADD COLUMN IF NOT EXISTS arrival_lng          NUMERIC(10,7),
    ADD COLUMN IF NOT EXISTS arrival_notes        TEXT,
    ADD COLUMN IF NOT EXISTS assignment_id        UUID REFERENCES fleet.assignments(id) ON DELETE SET NULL;

-- Les trajets existants n'avaient pas de destination : le lieu de retour en tient lieu.
UPDATE fleet.trips SET destination_location = COALESCE(return_location, departure_location)
WHERE destination_location IS NULL;

ALTER TABLE fleet.trips DROP CONSTRAINT IF EXISTS trips_status_check;
UPDATE fleet.trips SET status = 'ARRIVED' WHERE status = 'RETURNING';
ALTER TABLE fleet.trips ADD CONSTRAINT trips_status_check
    CHECK (status IN ('SCHEDULED', 'DEPARTED', 'ARRIVED', 'COMPLETED', 'CANCELLED', 'MISSED'));

ALTER TABLE fleet.trip_details ADD COLUMN IF NOT EXISTS delivered_quantity INTEGER CHECK (delivered_quantity >= 0);

CREATE INDEX IF NOT EXISTS idx_trips_assignment ON fleet.trips (assignment_id) WHERE assignment_id IS NOT NULL;

COMMENT ON COLUMN fleet.trips.mission_cost IS 'Archive (avant 058) : la tarification relève du cœur G1.';
COMMENT ON COLUMN fleet.trips.rate_type IS 'Archive (avant 058) : la tarification relève du cœur G1.';

--changeset fleetman:058-trip-dead-fields
ALTER TABLE fleet.trips DROP COLUMN IF EXISTS vehicle_type_id;
DROP TABLE IF EXISTS fleet.routes;

--changeset fleetman:058-assignment-shift
-- Rattachement des trajets existants au poste qui les couvre (même binôme, créneau inclus).
UPDATE fleet.trips t SET assignment_id = a.id
FROM fleet.assignments a
WHERE t.assignment_id IS NULL AND a.vehicle_id = t.vehicle_id AND a.driver_id = t.driver_id
  AND a.status <> 'CANCELLED'
  AND (t.start_date + COALESCE(t.start_time, TIME '00:00')) >= a.start_datetime
  AND (t.start_date + COALESCE(t.start_time, TIME '00:00')) < a.end_datetime;

ALTER TABLE fleet.assignments
    DROP COLUMN IF EXISTS start_location,
    DROP COLUMN IF EXISTS end_location,
    DROP COLUMN IF EXISTS estimated_km,
    DROP COLUMN IF EXISTS mission_id;

--changeset fleetman:058-mission-complements
ALTER TABLE fleet.trip_mission_submissions
    ADD COLUMN IF NOT EXISTS change_type VARCHAR(10) NOT NULL DEFAULT 'ADD' CHECK (change_type IN ('ADD', 'REMOVE')),
    ADD COLUMN IF NOT EXISTS detail_id   UUID REFERENCES fleet.trip_details(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS rejection_reason TEXT;

--changeset fleetman:058-active-trip-indexes
-- Index partiels des trajets en cours : RETURNING n'existe plus, ARRIVED le remplace.
DROP INDEX IF EXISTS fleet.idx_trips_driver_active;
DROP INDEX IF EXISTS fleet.idx_trips_vehicle_active;
CREATE INDEX idx_trips_driver_active ON fleet.trips (driver_id, status) WHERE status IN ('DEPARTED', 'ARRIVED');
CREATE INDEX idx_trips_vehicle_active ON fleet.trips (vehicle_id, status) WHERE status IN ('DEPARTED', 'ARRIVED');
