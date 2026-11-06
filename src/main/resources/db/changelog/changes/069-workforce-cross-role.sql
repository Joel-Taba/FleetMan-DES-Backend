--liquibase formatted sql

-- Interactions gestionnaire <-> chauffeur du domaine « effectifs » :
--   * postes : marqueurs pour ne notifier qu'une fois un poste « absent » (NO_SHOW) et pour alerter
--     le gestionnaire d'un poste modifié resté sans accusé de réception à l'approche de son début ;
--   * demandes d'absence du chauffeur (congé, arrêt maladie) validées ou refusées par le gestionnaire ;
--   * journal des relevés terrain (compteur, carburant) saisis par le chauffeur, avec anomalies ;
--   * pièces demandées par le contrôleur à une personne (chauffeur ou gestionnaire).

--changeset fleetman:069-assignment-alert-markers
ALTER TABLE fleet.assignments
    ADD COLUMN IF NOT EXISTS no_show_notified_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS unack_alerted_at    TIMESTAMP;
-- Les postes déjà « absents » ne déclenchent pas de notification rétroactive.
UPDATE fleet.assignments SET no_show_notified_at = COALESCE(updated_at, created_at, NOW())
WHERE status = 'NO_SHOW' AND no_show_notified_at IS NULL;

--changeset fleetman:069-driver-absence-requests
CREATE TABLE IF NOT EXISTS fleet.driver_absence_requests (
    id              UUID PRIMARY KEY,
    driver_id       UUID         NOT NULL REFERENCES fleet.drivers(user_id) ON DELETE CASCADE,
    type            VARCHAR(30)  NOT NULL CHECK (type IN ('LEAVE', 'SICK_LEAVE')),
    start_date      DATE         NOT NULL,
    end_date        DATE         NOT NULL,
    reason          VARCHAR(500),
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    decided_by      UUID,
    decided_at      TIMESTAMP,
    decision_reason VARCHAR(500),
    absence_id      UUID,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    tenant_id       UUID,
    CONSTRAINT chk_absence_request_dates CHECK (end_date >= start_date)
);
CREATE INDEX IF NOT EXISTS idx_absence_requests_driver
    ON fleet.driver_absence_requests (driver_id, created_at DESC);

--changeset fleetman:069-vehicle-operational-updates
CREATE TABLE IF NOT EXISTS fleet.vehicle_operational_updates (
    id              UUID PRIMARY KEY,
    vehicle_id      UUID         NOT NULL REFERENCES fleet.vehicles(id) ON DELETE CASCADE,
    author_id       UUID,
    odometer_before NUMERIC(12,1),
    odometer_after  NUMERIC(12,1),
    fuel_before     VARCHAR(20),
    fuel_after      VARCHAR(20),
    accepted        BOOLEAN      NOT NULL DEFAULT TRUE,
    anomalies       VARCHAR(200),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_vehicle_operational_updates
    ON fleet.vehicle_operational_updates (vehicle_id, created_at DESC);

--changeset fleetman:069-document-requests
CREATE TABLE IF NOT EXISTS fleet.document_requests (
    id           UUID PRIMARY KEY,
    target_type  VARCHAR(20)  NOT NULL,
    target_id    UUID         NOT NULL,
    doc_type     VARCHAR(60)  NOT NULL,
    message      VARCHAR(500),
    requested_by UUID,
    requested_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    fulfilled_at TIMESTAMP,
    document_id  UUID
);
CREATE INDEX IF NOT EXISTS idx_document_requests_open
    ON fleet.document_requests (target_type, target_id, doc_type) WHERE fulfilled_at IS NULL;
