--liquibase formatted sql

--changeset fleet-extensions:create-kpi-tables-v1 splitStatements:true
-- ============================================================
-- MODULE 3 — KPI ET RAPPORTS AVANCÉS
-- Table : kpi_snapshots
-- Schema : fleet
-- ============================================================

CREATE TABLE IF NOT EXISTS fleet.kpi_snapshots (
    id                      UUID PRIMARY KEY,
    fleet_id                UUID NOT NULL REFERENCES fleet.fleets(id) ON DELETE CASCADE,
    entity_type             VARCHAR(20) NOT NULL
                            CHECK (entity_type IN ('FLEET','VEHICLE','DRIVER')),
    entity_id               UUID NOT NULL,
    period_type             VARCHAR(10) NOT NULL
                            CHECK (period_type IN ('DAILY','WEEKLY','MONTHLY')),
    period_start            DATE NOT NULL,
    period_end              DATE NOT NULL,

    -- KPIs operationnels
    total_km                NUMERIC(14,2) DEFAULT 0,
    total_trips             INT DEFAULT 0,
    total_driving_hours     NUMERIC(10,2) DEFAULT 0,
    availability_rate       NUMERIC(6,2),

    -- KPIs financiers
    total_fuel_cost         NUMERIC(14,2) DEFAULT 0,
    total_fuel_liters       NUMERIC(12,2) DEFAULT 0,
    total_maintenance_cost  NUMERIC(14,2) DEFAULT 0,
    total_incident_cost     NUMERIC(14,2) DEFAULT 0,
    cost_per_km             NUMERIC(10,4),
    fuel_per_100km          NUMERIC(8,2),

    -- KPIs securite
    total_incidents         INT DEFAULT 0,
    incident_rate           NUMERIC(8,4),
    avg_driver_score        NUMERIC(6,2),

    -- KPIs conformite
    doc_compliance_rate     NUMERIC(6,2),

    calculated_at           TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT uq_kpi_entity_period
        UNIQUE (entity_type, entity_id, period_type, period_start)
);

CREATE INDEX IF NOT EXISTS idx_kpi_fleet_period
    ON fleet.kpi_snapshots(fleet_id, period_type, period_start DESC);
CREATE INDEX IF NOT EXISTS idx_kpi_entity
    ON fleet.kpi_snapshots(entity_type, entity_id, period_start DESC);
CREATE INDEX IF NOT EXISTS idx_kpi_vehicle_km
    ON fleet.kpi_snapshots(fleet_id, entity_type, total_km DESC)
    WHERE entity_type = 'VEHICLE';
CREATE INDEX IF NOT EXISTS idx_kpi_driver_score
    ON fleet.kpi_snapshots(fleet_id, entity_type, avg_driver_score DESC)
    WHERE entity_type = 'DRIVER';
