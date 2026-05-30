--liquibase formatted sql

--changeset operations-team:create-operations-tables-v1 splitStatements:true

-- ============================================================
-- MODULE OPÉRATIONS TERRAIN
-- Tables : maintenances, incidents, fuel_recharges
-- Schéma : fleet
-- Dépendances : fleet.vehicles, fleet.drivers (002-fleet-tables.sql)
-- ============================================================


-- 1. TABLE MAINTENANCES
-- Enregistre les interventions techniques sur les véhicules.
CREATE TABLE IF NOT EXISTS fleet.maintenances (
    id                   UUID PRIMARY KEY,
    subject              VARCHAR(255) NOT NULL,
    cost                 NUMERIC(12, 2),
    date_time            TIMESTAMP NOT NULL DEFAULT now(),
    report               TEXT,
    longitude            NUMERIC(10, 7),
    latitude             NUMERIC(10, 7),
    location_name        VARCHAR(255),
    vehicle_id           UUID NOT NULL REFERENCES fleet.vehicles(id) ON DELETE CASCADE,
    vehicle_registration VARCHAR(50),
    driver_id            UUID REFERENCES fleet.drivers(user_id) ON DELETE SET NULL,
    driver_full_name     VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_maintenances_vehicle_id  ON fleet.maintenances(vehicle_id);
CREATE INDEX IF NOT EXISTS idx_maintenances_driver_id   ON fleet.maintenances(driver_id);
CREATE INDEX IF NOT EXISTS idx_maintenances_date_time   ON fleet.maintenances(date_time DESC);


-- 2. TABLE INCIDENTS
-- Enregistre les événements imprévus sur les véhicules avec cycle de vie complet.
CREATE TABLE IF NOT EXISTS fleet.incidents (
    id                      UUID PRIMARY KEY,
    type                    VARCHAR(50) NOT NULL
                                CHECK (type IN ('ACCIDENT','BREAKDOWN','THEFT','VANDALISM','TRAFFIC_VIOLATION','OTHER')),
    description             TEXT,
    severity                VARCHAR(50) DEFAULT 'MEDIUM'
                                CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    incident_date_time      TIMESTAMP NOT NULL DEFAULT now(),
    longitude               NUMERIC(10, 7),
    latitude                NUMERIC(10, 7),
    cost                    NUMERIC(12, 2),
    status                  VARCHAR(50) DEFAULT 'REPORTED'
                                CHECK (status IN ('REPORTED','UNDER_INVESTIGATION','RESOLVED','CLOSED')),
    report                  TEXT,
    witness_name            VARCHAR(255),
    witness_contact         VARCHAR(255),
    police_report_number    VARCHAR(100),
    insurance_claim_number  VARCHAR(100),
    reported_by             VARCHAR(255),
    resolved_at             TIMESTAMP,
    vehicle_id              UUID NOT NULL REFERENCES fleet.vehicles(id) ON DELETE CASCADE,
    vehicle_registration    VARCHAR(50),
    driver_id               UUID REFERENCES fleet.drivers(user_id) ON DELETE SET NULL,
    driver_full_name        VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_incidents_vehicle_id        ON fleet.incidents(vehicle_id);
CREATE INDEX IF NOT EXISTS idx_incidents_driver_id         ON fleet.incidents(driver_id);
CREATE INDEX IF NOT EXISTS idx_incidents_status            ON fleet.incidents(status);
CREATE INDEX IF NOT EXISTS idx_incidents_severity          ON fleet.incidents(severity);
CREATE INDEX IF NOT EXISTS idx_incidents_date_time         ON fleet.incidents(incident_date_time DESC);


-- 3. TABLE FUEL_RECHARGES
-- Enregistre les pleins de carburant effectués sur les véhicules.
CREATE TABLE IF NOT EXISTS fleet.fuel_recharges (
    id                   UUID PRIMARY KEY,
    quantity             NUMERIC(10, 2) NOT NULL,
    price                NUMERIC(12, 2) NOT NULL,
    recharge_date_time   TIMESTAMP NOT NULL DEFAULT now(),
    longitude            NUMERIC(10, 7),
    latitude             NUMERIC(10, 7),
    station_name         VARCHAR(50)
                             CHECK (station_name IN ('TOTAL','SHELL','OILIBYA','CAMRAIL','OTHER')),
    vehicle_id           UUID NOT NULL REFERENCES fleet.vehicles(id) ON DELETE CASCADE,
    vehicle_registration VARCHAR(50),
    driver_id            UUID REFERENCES fleet.drivers(user_id) ON DELETE SET NULL,
    driver_full_name     VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_fuel_recharges_vehicle_id      ON fleet.fuel_recharges(vehicle_id);
CREATE INDEX IF NOT EXISTS idx_fuel_recharges_driver_id       ON fleet.fuel_recharges(driver_id);
CREATE INDEX IF NOT EXISTS idx_fuel_recharges_date_time       ON fleet.fuel_recharges(recharge_date_time DESC);
