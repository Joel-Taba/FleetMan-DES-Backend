--liquibase formatted sql

-- Seuls les chauffeurs et les véhicules changent de flotte. Les incidents, entretiens et pleins
-- restent attachés à la flotte où ils ont eu lieu : leur flotte est figée à la création
-- (déclencheur : flotte du véhicule à ce moment-là), et l'historique existant est rattaché
-- à la flotte actuelle de son véhicule.

--changeset fleetman:075-operations-fleet-columns
ALTER TABLE fleet.incidents ADD COLUMN IF NOT EXISTS fleet_id UUID;
ALTER TABLE fleet.maintenances ADD COLUMN IF NOT EXISTS fleet_id UUID;
ALTER TABLE fleet.fuel_recharges ADD COLUMN IF NOT EXISTS fleet_id UUID;
UPDATE fleet.incidents i SET fleet_id = v.fleet_id FROM fleet.vehicles v WHERE v.id = i.vehicle_id AND i.fleet_id IS NULL;
UPDATE fleet.maintenances m SET fleet_id = v.fleet_id FROM fleet.vehicles v WHERE v.id = m.vehicle_id AND m.fleet_id IS NULL;
UPDATE fleet.fuel_recharges f SET fleet_id = v.fleet_id FROM fleet.vehicles v WHERE v.id = f.vehicle_id AND f.fleet_id IS NULL;
CREATE INDEX IF NOT EXISTS ix_incidents_fleet ON fleet.incidents (fleet_id);
CREATE INDEX IF NOT EXISTS ix_maintenances_fleet ON fleet.maintenances (fleet_id);
CREATE INDEX IF NOT EXISTS ix_fuel_recharges_fleet ON fleet.fuel_recharges (fleet_id);

--changeset fleetman:075-operations-fleet-trigger splitStatements:false
CREATE OR REPLACE FUNCTION fleet.set_operation_fleet() RETURNS trigger AS $fn$
BEGIN
    IF NEW.fleet_id IS NULL AND NEW.vehicle_id IS NOT NULL THEN
        SELECT v.fleet_id INTO NEW.fleet_id FROM fleet.vehicles v WHERE v.id = NEW.vehicle_id;
    END IF;
    RETURN NEW;
END;
$fn$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_incidents_fleet ON fleet.incidents;
CREATE TRIGGER trg_incidents_fleet BEFORE INSERT ON fleet.incidents FOR EACH ROW EXECUTE FUNCTION fleet.set_operation_fleet();
DROP TRIGGER IF EXISTS trg_maintenances_fleet ON fleet.maintenances;
CREATE TRIGGER trg_maintenances_fleet BEFORE INSERT ON fleet.maintenances FOR EACH ROW EXECUTE FUNCTION fleet.set_operation_fleet();
DROP TRIGGER IF EXISTS trg_fuel_recharges_fleet ON fleet.fuel_recharges;
CREATE TRIGGER trg_fuel_recharges_fleet BEFORE INSERT ON fleet.fuel_recharges FOR EACH ROW EXECUTE FUNCTION fleet.set_operation_fleet();
