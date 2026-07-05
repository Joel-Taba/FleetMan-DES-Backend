--liquibase formatted sql

--changeset fleet-extensions:add-soft-delete-and-audit splitStatements:true
-- ============================================================
-- CHAPITRE 1 — EXIGENCES GLOBALES : SOFT DELETE ET AUDIT TRAIL
--
-- Ajoute les colonnes de traçabilité et de suppression logique
-- sur les tables critiques du schéma fleet.
--
-- Tables concernées :
--   fleet.vehicles, fleet.drivers, fleet.fleets,
--   fleet.maintenances, fleet.incidents, fleet.fuel_recharges
-- ============================================================


-- ─────────────────────────────────────────────────────────────
-- 1. TABLE fleet.vehicles — Soft Delete + Audit
-- ─────────────────────────────────────────────────────────────
ALTER TABLE fleet.vehicles
    ADD COLUMN IF NOT EXISTS deleted_at   TIMESTAMP DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS created_at   TIMESTAMP DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at   TIMESTAMP DEFAULT now(),
    ADD COLUMN IF NOT EXISTS created_by   UUID DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS updated_by   UUID DEFAULT NULL;

CREATE INDEX IF NOT EXISTS idx_vehicles_deleted_at
    ON fleet.vehicles(deleted_at) WHERE deleted_at IS NULL;


-- ─────────────────────────────────────────────────────────────
-- 2. TABLE fleet.drivers — Soft Delete + Audit
-- ─────────────────────────────────────────────────────────────
ALTER TABLE fleet.drivers
    ADD COLUMN IF NOT EXISTS deleted_at   TIMESTAMP DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS created_at   TIMESTAMP DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at   TIMESTAMP DEFAULT now(),
    ADD COLUMN IF NOT EXISTS created_by   UUID DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS updated_by   UUID DEFAULT NULL;

CREATE INDEX IF NOT EXISTS idx_drivers_deleted_at
    ON fleet.drivers(deleted_at) WHERE deleted_at IS NULL;


-- ─────────────────────────────────────────────────────────────
-- 3. TABLE fleet.fleets — Soft Delete + Audit
-- ─────────────────────────────────────────────────────────────
ALTER TABLE fleet.fleets
    ADD COLUMN IF NOT EXISTS deleted_at   TIMESTAMP DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS updated_at   TIMESTAMP DEFAULT now(),
    ADD COLUMN IF NOT EXISTS created_by   UUID DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS updated_by   UUID DEFAULT NULL;

CREATE INDEX IF NOT EXISTS idx_fleets_deleted_at
    ON fleet.fleets(deleted_at) WHERE deleted_at IS NULL;


-- ─────────────────────────────────────────────────────────────
-- 4. TABLE fleet.maintenances — Audit Trail
-- ─────────────────────────────────────────────────────────────
ALTER TABLE fleet.maintenances
    ADD COLUMN IF NOT EXISTS updated_at   TIMESTAMP DEFAULT now(),
    ADD COLUMN IF NOT EXISTS created_by   UUID DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS updated_by   UUID DEFAULT NULL;


-- ─────────────────────────────────────────────────────────────
-- 5. TABLE fleet.incidents — Audit Trail
-- ─────────────────────────────────────────────────────────────
ALTER TABLE fleet.incidents
    ADD COLUMN IF NOT EXISTS updated_at   TIMESTAMP DEFAULT now(),
    ADD COLUMN IF NOT EXISTS created_by   UUID DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS updated_by   UUID DEFAULT NULL;


-- ─────────────────────────────────────────────────────────────
-- 6. TABLE fleet.fuel_recharges — Audit Trail
-- ─────────────────────────────────────────────────────────────
ALTER TABLE fleet.fuel_recharges
    ADD COLUMN IF NOT EXISTS updated_at   TIMESTAMP DEFAULT now(),
    ADD COLUMN IF NOT EXISTS created_by   UUID DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS updated_by   UUID DEFAULT NULL;


-- ─────────────────────────────────────────────────────────────
-- 7. TABLE fleet.trips — Soft Delete + Audit
-- ─────────────────────────────────────────────────────────────
ALTER TABLE fleet.trips
    ADD COLUMN IF NOT EXISTS deleted_at   TIMESTAMP DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS created_at   TIMESTAMP DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at   TIMESTAMP DEFAULT now(),
    ADD COLUMN IF NOT EXISTS created_by   UUID DEFAULT NULL;

CREATE INDEX IF NOT EXISTS idx_trips_deleted_at
    ON fleet.trips(deleted_at) WHERE deleted_at IS NULL;


-- ─────────────────────────────────────────────────────────────
-- 8. FONCTION utilitaire : mise à jour automatique de updated_at
-- Note : Liquibase ne supporte pas le dollar-quoting ($$).
-- On utilise une syntaxe alternative avec des guillemets simples echappes.
-- ─────────────────────────────────────────────────────────────

--changeset fleet-extensions:create-updated-at-function splitStatements:false
CREATE OR REPLACE FUNCTION fleet.update_updated_at_column()
RETURNS TRIGGER AS $func$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$func$ LANGUAGE plpgsql;

--changeset fleet-extensions:create-updated-at-triggers splitStatements:true
-- Triggers sur les tables principales
CREATE OR REPLACE TRIGGER trg_vehicles_updated_at
    BEFORE UPDATE ON fleet.vehicles
    FOR EACH ROW EXECUTE FUNCTION fleet.update_updated_at_column();

CREATE OR REPLACE TRIGGER trg_drivers_updated_at
    BEFORE UPDATE ON fleet.drivers
    FOR EACH ROW EXECUTE FUNCTION fleet.update_updated_at_column();

CREATE OR REPLACE TRIGGER trg_fleets_updated_at
    BEFORE UPDATE ON fleet.fleets
    FOR EACH ROW EXECUTE FUNCTION fleet.update_updated_at_column();

CREATE OR REPLACE TRIGGER trg_maintenances_updated_at
    BEFORE UPDATE ON fleet.maintenances
    FOR EACH ROW EXECUTE FUNCTION fleet.update_updated_at_column();

CREATE OR REPLACE TRIGGER trg_incidents_updated_at
    BEFORE UPDATE ON fleet.incidents
    FOR EACH ROW EXECUTE FUNCTION fleet.update_updated_at_column();

CREATE OR REPLACE TRIGGER trg_fuel_recharges_updated_at
    BEFORE UPDATE ON fleet.fuel_recharges
    FOR EACH ROW EXECUTE FUNCTION fleet.update_updated_at_column();
