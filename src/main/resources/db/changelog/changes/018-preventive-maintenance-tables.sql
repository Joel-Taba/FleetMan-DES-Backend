--liquibase formatted sql

--changeset preventive-team:create-preventive-maintenance-tables-v1 splitStatements:true
-- ============================================================
-- MODULE 6 — MAINTENANCE PRÉVENTIVE
-- Tables : maintenance_plans, maintenance_alerts
-- Schema : fleet
-- Dépendances : fleet.fleets, fleet.vehicles, fleet.fleet_managers, fleet.maintenances
-- ============================================================


-- ─────────────────────────────────────────────────────────────
-- 1. TABLE fleet.maintenance_plans — Plans de maintenance préventive
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS fleet.maintenance_plans (
    id               UUID PRIMARY KEY,

    maintenance_type VARCHAR(30) NOT NULL
                     CHECK (maintenance_type IN (
                         'OIL_CHANGE','TIRE_ROTATION','BRAKE_INSPECTION',
                         'FILTER_CHANGE','TIMING_BELT','COOLANT_FLUSH',
                         'TRANSMISSION_SERVICE','GENERAL_INSPECTION','OTHER'
                     )),

    -- FLEET = applicable à tous les véhicules de la flotte
    -- VEHICLE = surcharge pour un véhicule spécifique
    scope            VARCHAR(10) NOT NULL
                     CHECK (scope IN ('FLEET','VEHICLE')),

    fleet_id         UUID NOT NULL REFERENCES fleet.fleets(id) ON DELETE CASCADE,

    -- Null si scope = FLEET
    vehicle_id       UUID REFERENCES fleet.vehicles(id) ON DELETE CASCADE,

    manager_id       UUID NOT NULL REFERENCES fleet.fleet_managers(user_id) ON DELETE CASCADE,

    label            VARCHAR(255),
    description      TEXT,

    -- Seuils de déclenchement (au moins l'un doit être non-null)
    interval_km      INT CHECK (interval_km > 0),
    interval_days    INT CHECK (interval_days > 0),

    -- Zones de préalerte
    pre_alert_km     INT CHECK (pre_alert_km >= 0),
    pre_alert_days   INT CHECK (pre_alert_days >= 0),

    active           BOOLEAN NOT NULL DEFAULT TRUE,

    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT chk_plan_has_threshold
        CHECK (interval_km IS NOT NULL OR interval_days IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_maint_plans_fleet
    ON fleet.maintenance_plans(fleet_id, maintenance_type);

CREATE INDEX IF NOT EXISTS idx_maint_plans_vehicle
    ON fleet.maintenance_plans(vehicle_id)
    WHERE vehicle_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_maint_plans_manager
    ON fleet.maintenance_plans(manager_id, active);

CREATE OR REPLACE TRIGGER trg_maint_plans_updated_at
    BEFORE UPDATE ON fleet.maintenance_plans
    FOR EACH ROW EXECUTE FUNCTION fleet.update_updated_at_column();


-- ─────────────────────────────────────────────────────────────
-- 2. TABLE fleet.maintenance_alerts — Alertes de maintenance préventive
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS fleet.maintenance_alerts (
    id                          UUID PRIMARY KEY,
    plan_id                     UUID NOT NULL REFERENCES fleet.maintenance_plans(id) ON DELETE CASCADE,

    maintenance_type            VARCHAR(30) NOT NULL,

    vehicle_id                  UUID NOT NULL REFERENCES fleet.vehicles(id) ON DELETE CASCADE,
    vehicle_registration        VARCHAR(50),

    fleet_id                    UUID NOT NULL REFERENCES fleet.fleets(id) ON DELETE CASCADE,
    manager_id                  UUID NOT NULL REFERENCES fleet.fleet_managers(user_id) ON DELETE CASCADE,

    -- UPCOMING → DUE → OVERDUE → RESOLVED
    status                      VARCHAR(15) NOT NULL DEFAULT 'UPCOMING'
                                CHECK (status IN ('UPCOMING','DUE','OVERDUE','RESOLVED')),

    -- MILEAGE, DATE, BOTH
    trigger_type                VARCHAR(10)
                                CHECK (trigger_type IN ('MILEAGE','DATE','BOTH')),

    -- Données kilométriques
    last_maintenance_km         REAL,
    target_km                   REAL,
    current_km                  REAL,
    km_remaining                REAL,

    -- Données temporelles
    last_maintenance_date       DATE,
    target_date                 DATE,
    days_remaining              INT,

    -- Résolution
    resolved_by_maintenance_id  UUID REFERENCES fleet.maintenances(id) ON DELETE SET NULL,
    resolved_at                 TIMESTAMP,

    created_at                  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMP NOT NULL DEFAULT now()
);

-- Index pour les requêtes fréquentes
CREATE INDEX IF NOT EXISTS idx_maint_alerts_manager_active
    ON fleet.maintenance_alerts(manager_id, status)
    WHERE status != 'RESOLVED';

CREATE INDEX IF NOT EXISTS idx_maint_alerts_urgent
    ON fleet.maintenance_alerts(manager_id, status)
    WHERE status IN ('DUE','OVERDUE');

CREATE INDEX IF NOT EXISTS idx_maint_alerts_vehicle
    ON fleet.maintenance_alerts(vehicle_id, status);

CREATE INDEX IF NOT EXISTS idx_maint_alerts_fleet_active
    ON fleet.maintenance_alerts(fleet_id, status)
    WHERE status != 'RESOLVED';

-- Index pour la déduplication (1 alerte active par véhicule et type)
CREATE UNIQUE INDEX IF NOT EXISTS uq_maint_alert_vehicle_type_active
    ON fleet.maintenance_alerts(vehicle_id, maintenance_type)
    WHERE status != 'RESOLVED';

CREATE OR REPLACE TRIGGER trg_maint_alerts_updated_at
    BEFORE UPDATE ON fleet.maintenance_alerts
    FOR EACH ROW EXECUTE FUNCTION fleet.update_updated_at_column();
