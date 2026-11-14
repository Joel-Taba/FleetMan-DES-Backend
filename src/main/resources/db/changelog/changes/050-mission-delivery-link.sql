--liquibase formatted sql

--changeset g4-team:050-mission-delivery-link splitStatements:true

-- ============================================================
-- G4 : MISSIONS ET PREUVES DE LIVRAISON (TiiBnTick & Fleetman)
-- Tables : fleet.missions, fleet.delivery_proofs
-- Objectif : Relier la commande/livraison TiiBnTick à la mission Fleetman,
--            au tracking Geofence et stocker la preuve vérifiable.
-- ============================================================

-- 1. TABLE MISSIONS
CREATE TABLE IF NOT EXISTS fleet.missions (
    id                      UUID PRIMARY KEY,
    vehicle_id              UUID NOT NULL REFERENCES fleet.vehicles(id) ON DELETE RESTRICT,
    driver_id               UUID NOT NULL REFERENCES fleet.drivers(user_id) ON DELETE RESTRICT,
    fleet_manager_id        UUID REFERENCES fleet.fleet_managers(user_id) ON DELETE SET NULL,
    tiibntick_delivery_id   UUID,
    tiibntick_parcel_id     VARCHAR(255),
    status                  VARCHAR(50) NOT NULL DEFAULT 'PENDING'
                                CHECK (status IN ('PENDING', 'ACTIVE', 'COMPLETED', 'CANCELLED')),
    type                    VARCHAR(50) NOT NULL DEFAULT 'DELIVERY'
                                CHECK (type IN ('DELIVERY', 'PICKUP', 'TRANSFER', 'ROUND_TRIP')),
    scheduled_start         TIMESTAMP NOT NULL,
    scheduled_end           TIMESTAMP,
    actual_start            TIMESTAMP,
    actual_end              TIMESTAMP,
    start_location_id       UUID,
    end_location_id         UUID,
    geofence_object_id      VARCHAR(255),
    notes                   TEXT,
    cancel_reason           TEXT,
    created_at              TIMESTAMP NOT NULL DEFAULT now(),
    tenant_id               UUID
);

CREATE INDEX IF NOT EXISTS idx_missions_vehicle_id       ON fleet.missions(vehicle_id);
CREATE INDEX IF NOT EXISTS idx_missions_driver_id        ON fleet.missions(driver_id);
CREATE INDEX IF NOT EXISTS idx_missions_manager_id       ON fleet.missions(fleet_manager_id);
CREATE INDEX IF NOT EXISTS idx_missions_status           ON fleet.missions(status);
CREATE INDEX IF NOT EXISTS idx_missions_tiibntick_deliv  ON fleet.missions(tiibntick_delivery_id);
CREATE INDEX IF NOT EXISTS idx_missions_geofence_obj     ON fleet.missions(geofence_object_id);
CREATE INDEX IF NOT EXISTS idx_missions_scheduled_time   ON fleet.missions(scheduled_start, scheduled_end);

-- 2. TABLE DELIVERY_PROOFS
CREATE TABLE IF NOT EXISTS fleet.delivery_proofs (
    id                      UUID PRIMARY KEY,
    mission_id              UUID NOT NULL REFERENCES fleet.missions(id) ON DELETE CASCADE,
    driver_id               UUID NOT NULL REFERENCES fleet.drivers(user_id) ON DELETE RESTRICT,
    type                    VARCHAR(50) NOT NULL DEFAULT 'PHOTO'
                                CHECK (type IN ('PHOTO', 'SIGNATURE', 'QR_SCAN', 'MANUAL_CONFIRMATION')),
    status                  VARCHAR(50) NOT NULL DEFAULT 'PENDING_REVIEW'
                                CHECK (status IN ('PENDING_REVIEW', 'ACCEPTED', 'REJECTED', 'TAMPERED')),
    file_id                 VARCHAR(255),
    file_url                TEXT,
    integrity_hash          VARCHAR(255),
    recipient_name          VARCHAR(255),
    recipient_signature     TEXT,
    latitude                NUMERIC(10, 7),
    longitude               NUMERIC(10, 7),
    notes                   TEXT,
    submitted_at            TIMESTAMP NOT NULL DEFAULT now(),
    reviewed_at             TIMESTAMP,
    reviewed_by             UUID,
    created_at              TIMESTAMP NOT NULL DEFAULT now(),
    tenant_id               UUID
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_delivery_proofs_mission_id ON fleet.delivery_proofs(mission_id);
CREATE INDEX IF NOT EXISTS idx_delivery_proofs_driver_id  ON fleet.delivery_proofs(driver_id);
CREATE INDEX IF NOT EXISTS idx_delivery_proofs_status     ON fleet.delivery_proofs(status);

