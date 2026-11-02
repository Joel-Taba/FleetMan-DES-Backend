--liquibase formatted sql

-- Refonte de la gestion des véhicules et des chauffeurs.
--
-- Véhicules :
--   * caractéristiques techniques conservées localement (VIN, carburant, boîte, réservoir,
--     places, consommation de référence) : la ressource Kernel ne les stocke pas, elles
--     étaient perdues dès la création alors que le suivi carburant en dépend ;
--   * cycle de vie complet : AVAILABLE, ON_TRIP (piloté par les trajets), MAINTENANCE,
--     IMMOBILIZED (non conforme / hors service, motif obligatoire), RETIRED (réformé ou cédé :
--     date, motif, historique conservé, place du plan libérée) ;
--   * immatriculation unique une fois normalisée (« lt 892 ce » = « LT-892-CE ») ;
--   * compteur kilométrique unique : odometer_reading fait foi, mileage le recopie.
--
-- Chauffeurs :
--   * statut contractuel ACTIVE ou LEFT (a quitté l'entreprise, historique conservé) ;
--   * absences datées (congé, arrêt maladie, suspension) : le chauffeur n'est pas engageable
--     sur ces jours et redevient disponible automatiquement à la fin de l'absence.

--changeset fleetman:055-vehicle-specs
ALTER TABLE fleet.vehicles
    ADD COLUMN IF NOT EXISTS vehicle_serial_number    VARCHAR(50),
    ADD COLUMN IF NOT EXISTS fuel_type                VARCHAR(30),
    ADD COLUMN IF NOT EXISTS transmission_type        VARCHAR(30),
    ADD COLUMN IF NOT EXISTS tank_capacity            NUMERIC(8,2),
    ADD COLUMN IF NOT EXISTS total_seat_number        INTEGER,
    ADD COLUMN IF NOT EXISTS average_fuel_consumption NUMERIC(6,2);

ALTER TABLE fleet.vehicles
    ADD CONSTRAINT chk_vehicle_tank_capacity CHECK (tank_capacity IS NULL OR tank_capacity > 0),
    ADD CONSTRAINT chk_vehicle_seats CHECK (total_seat_number IS NULL OR total_seat_number > 0),
    ADD CONSTRAINT chk_vehicle_consumption CHECK (average_fuel_consumption IS NULL OR average_fuel_consumption > 0),
    ADD CONSTRAINT chk_vehicle_year CHECK (manufacturing_year IS NULL OR manufacturing_year BETWEEN 1950 AND 2100);

--changeset fleetman:055-vehicle-lifecycle
ALTER TABLE fleet.vehicles
    ADD COLUMN IF NOT EXISTS status_reason     VARCHAR(500),
    ADD COLUMN IF NOT EXISTS status_changed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS retired_at        DATE,
    ADD COLUMN IF NOT EXISTS retirement_type   VARCHAR(30),
    ADD COLUMN IF NOT EXISTS retirement_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS sale_price        NUMERIC(14,2);

ALTER TABLE fleet.vehicles DROP CONSTRAINT IF EXISTS vehicles_status_check;
ALTER TABLE fleet.vehicles
    ADD CONSTRAINT vehicles_status_check
        CHECK (status IN ('AVAILABLE', 'ON_TRIP', 'MAINTENANCE', 'IMMOBILIZED', 'RETIRED')),
    ADD CONSTRAINT chk_vehicle_retirement_type
        CHECK (retirement_type IS NULL OR retirement_type IN ('SOLD', 'SCRAPPED', 'STOLEN', 'TOTAL_LOSS', 'RETURNED', 'OTHER')),
    -- Un véhicule réformé a toujours une date et un type de sortie, et seulement lui.
    ADD CONSTRAINT chk_vehicle_retired_fields
        CHECK ((status = 'RETIRED') = (retired_at IS NOT NULL AND retirement_type IS NOT NULL)),
    ADD CONSTRAINT chk_vehicle_sale_price CHECK (sale_price IS NULL OR sale_price >= 0);

UPDATE fleet.vehicles SET status_changed_at = COALESCE(updated_at, created_at, NOW())
WHERE status_changed_at IS NULL;

--changeset fleetman:055-vehicle-plate-normalized
-- Les plaques sont stockées en majuscules ; l'unicité porte sur la forme sans séparateurs,
-- parmi les véhicules non supprimés (un véhicule supprimé par erreur ne bloque plus sa plaque).
UPDATE fleet.vehicles SET license_plate = UPPER(TRIM(license_plate)) WHERE license_plate <> UPPER(TRIM(license_plate));
ALTER TABLE fleet.vehicles DROP CONSTRAINT IF EXISTS vehicles_license_plate_key;
DROP INDEX IF EXISTS fleet.vehicles_license_plate_key;
CREATE UNIQUE INDEX IF NOT EXISTS uq_vehicles_plate_normalized
    ON fleet.vehicles (REGEXP_REPLACE(UPPER(license_plate), '[^A-Z0-9]', '', 'g'))
    WHERE deleted_at IS NULL;

--changeset fleetman:055-vehicle-odometer
-- Deux compteurs divergeaient (ex. 3 090 km d'un côté, 12 345 km de l'autre) : le plus élevé
-- est le dernier relevé connu, les deux colonnes sont alignées sur lui.
UPDATE fleet.operational_parameters
SET odometer_reading = GREATEST(COALESCE(odometer_reading, 0), COALESCE(mileage, 0)),
    mileage          = GREATEST(COALESCE(odometer_reading, 0), COALESCE(mileage, 0))
WHERE odometer_reading IS DISTINCT FROM mileage;

-- Un véhicule sans ligne opérationnelle n'avait ni compteur ni niveau de carburant.
INSERT INTO fleet.operational_parameters (id, vehicle_id, status, odometer_reading, mileage, timestamp, tenant_id)
SELECT gen_random_uuid(), v.id, TRUE, NULL, NULL, NOW(), v.tenant_id
FROM fleet.vehicles v
WHERE NOT EXISTS (SELECT 1 FROM fleet.operational_parameters o WHERE o.vehicle_id = v.id);

--changeset fleetman:055-driver-lifecycle
ALTER TABLE fleet.drivers
    ADD COLUMN IF NOT EXISTS hire_date   DATE,
    ADD COLUMN IF NOT EXISTS left_at     DATE,
    ADD COLUMN IF NOT EXISTS left_reason VARCHAR(500);

-- INACTIVE (désactivé ou supprimé) devient LEFT : le chauffeur ne fait plus partie des effectifs.
UPDATE fleet.drivers
SET status = 'LEFT', left_at = COALESCE(deleted_at::date, updated_at::date, CURRENT_DATE)
WHERE status = 'INACTIVE' OR status IS NULL;

UPDATE fleet.drivers SET hire_date = created_at::date WHERE hire_date IS NULL;

ALTER TABLE fleet.drivers DROP CONSTRAINT IF EXISTS drivers_status_check;
ALTER TABLE fleet.drivers
    ADD CONSTRAINT drivers_status_check CHECK (status IN ('ACTIVE', 'LEFT')),
    ADD CONSTRAINT chk_driver_left_fields CHECK ((status = 'LEFT') = (left_at IS NOT NULL));

-- Un chauffeur parti ne garde pas de véhicule attitré.
UPDATE fleet.vehicles v SET current_driver_id = NULL
FROM fleet.drivers d
WHERE v.current_driver_id = d.user_id AND d.status = 'LEFT';
UPDATE fleet.drivers SET assigned_vehicle_id = NULL WHERE status = 'LEFT';

-- Le lien véhicule attitré est porté des deux côtés : on réaligne le côté véhicule.
UPDATE fleet.vehicles v SET current_driver_id = d.user_id
FROM fleet.drivers d
WHERE d.assigned_vehicle_id = v.id AND d.deleted_at IS NULL
  AND v.current_driver_id IS DISTINCT FROM d.user_id;
UPDATE fleet.vehicles v SET current_driver_id = NULL
WHERE v.current_driver_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM fleet.drivers d
                  WHERE d.user_id = v.current_driver_id AND d.assigned_vehicle_id = v.id);

--changeset fleetman:055-driver-absences
CREATE TABLE IF NOT EXISTS fleet.driver_absences (
    id           UUID PRIMARY KEY,
    driver_id    UUID         NOT NULL REFERENCES fleet.drivers(user_id) ON DELETE CASCADE,
    type         VARCHAR(30)  NOT NULL CHECK (type IN ('LEAVE', 'SICK_LEAVE', 'SUSPENSION')),
    start_date   DATE         NOT NULL,
    end_date     DATE,
    reason       VARCHAR(500),
    created_by   UUID,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    cancelled_at TIMESTAMP,
    tenant_id    UUID,
    -- Seule une suspension peut rester ouverte (fin inconnue) ; un congé a toujours une fin.
    CONSTRAINT chk_absence_dates CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT chk_absence_open_end CHECK (end_date IS NOT NULL OR type = 'SUSPENSION')
);
CREATE INDEX IF NOT EXISTS idx_driver_absences_driver
    ON fleet.driver_absences (driver_id, start_date) WHERE cancelled_at IS NULL;

--changeset fleetman:055-vehicle-manager-follows-fleet
-- Une flotte réattribuée à un autre gestionnaire gardait ses véhicules rattachés à l'ancien :
-- il les voyait encore et ils comptaient dans son abonnement. Le gestionnaire du véhicule est
-- celui de sa flotte.
UPDATE fleet.vehicles v SET manager_id = f.manager_id
FROM fleet.fleets f
WHERE v.fleet_id = f.id AND f.manager_id IS NOT NULL AND v.manager_id IS DISTINCT FROM f.manager_id
  AND EXISTS (SELECT 1 FROM fleet.fleet_managers fm WHERE fm.user_id = f.manager_id);
