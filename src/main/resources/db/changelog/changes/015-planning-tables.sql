--liquibase formatted sql

--changeset fleet-extensions:create-planning-tables-v1 splitStatements:true
-- ============================================================
-- MODULE 1 — PLANIFICATION & ORDONNANCEMENT
-- Tables : schedules, assignments
-- Schema : fleet
-- Dependances : fleet.fleets, fleet.vehicles, fleet.drivers
-- ============================================================


-- ─────────────────────────────────────────────────────────────
-- 1. TABLE fleet.schedules — Plannings de service
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS fleet.schedules (
    id              UUID PRIMARY KEY,
    fleet_id        UUID NOT NULL REFERENCES fleet.fleets(id) ON DELETE CASCADE,
    manager_id      UUID NOT NULL REFERENCES fleet.fleet_managers(user_id),
    title           VARCHAR(255) NOT NULL,
    period_type     VARCHAR(20) NOT NULL
                    CHECK (period_type IN ('DAILY','WEEKLY','MONTHLY')),
    start_date      DATE NOT NULL,
    end_date        DATE NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                    CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED')),
    notes           TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now(),
    created_by      UUID,
    CONSTRAINT chk_schedule_dates CHECK (end_date >= start_date)
);

CREATE INDEX IF NOT EXISTS idx_schedules_fleet
    ON fleet.schedules(fleet_id, start_date DESC);
CREATE INDEX IF NOT EXISTS idx_schedules_manager
    ON fleet.schedules(manager_id, status);

CREATE OR REPLACE TRIGGER trg_schedules_updated_at
    BEFORE UPDATE ON fleet.schedules
    FOR EACH ROW EXECUTE FUNCTION fleet.update_updated_at_column();


-- ─────────────────────────────────────────────────────────────
-- 2. TABLE fleet.assignments — Affectations vehicule-conducteur
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS fleet.assignments (
    id              UUID PRIMARY KEY,
    schedule_id     UUID REFERENCES fleet.schedules(id) ON DELETE SET NULL,
    fleet_id        UUID NOT NULL REFERENCES fleet.fleets(id) ON DELETE CASCADE,
    vehicle_id      UUID NOT NULL REFERENCES fleet.vehicles(id) ON DELETE CASCADE,
    driver_id       UUID NOT NULL REFERENCES fleet.drivers(user_id) ON DELETE CASCADE,
    mission_id      UUID,   -- FK vers fleet.missions (Module 7, ajoutee plus tard)
    start_datetime  TIMESTAMP NOT NULL,
    end_datetime    TIMESTAMP NOT NULL,
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING','IN_PROGRESS',
                                      'COMPLETED','CANCELLED','NO_SHOW')),
    start_location  VARCHAR(255),
    end_location    VARCHAR(255),
    estimated_km    NUMERIC(10,2),
    actual_km       NUMERIC(10,2),
    notes           TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_assignment_dates CHECK (end_datetime > start_datetime)
);

-- Index pour la detection de conflits (requetes critiques)
CREATE INDEX IF NOT EXISTS idx_assignments_vehicle_time
    ON fleet.assignments(vehicle_id, start_datetime, end_datetime)
    WHERE status IN ('PENDING','IN_PROGRESS');

CREATE INDEX IF NOT EXISTS idx_assignments_driver_time
    ON fleet.assignments(driver_id, start_datetime, end_datetime)
    WHERE status IN ('PENDING','IN_PROGRESS');

CREATE INDEX IF NOT EXISTS idx_assignments_fleet
    ON fleet.assignments(fleet_id, start_datetime DESC);

CREATE INDEX IF NOT EXISTS idx_assignments_schedule
    ON fleet.assignments(schedule_id)
    WHERE schedule_id IS NOT NULL;

CREATE OR REPLACE TRIGGER trg_assignments_updated_at
    BEFORE UPDATE ON fleet.assignments
    FOR EACH ROW EXECUTE FUNCTION fleet.update_updated_at_column();
