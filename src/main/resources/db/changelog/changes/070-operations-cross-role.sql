--liquibase formatted sql

-- Opérations terrain entre chauffeur et gestionnaire :
--   * plein : relevé kilométrique, nom réel de la station, photo du ticket, auteur de la saisie ;
--   * maintenance : cycle de vie d'une demande (demandée → planifiée → en cours → terminée /
--     annulée), type, urgence, date prévue, motif d'annulation, demandeur ;
--   * centre de notifications : index du compteur de non lues.

--changeset fleetman:070-fuel-recharge-details
ALTER TABLE fleet.fuel_recharges
    ADD COLUMN IF NOT EXISTS odometer_km   NUMERIC(12,1),
    ADD COLUMN IF NOT EXISTS station_label VARCHAR(255),
    ADD COLUMN IF NOT EXISTS receipt_url   TEXT,
    ADD COLUMN IF NOT EXISTS receipt_name  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS receipt_mime  VARCHAR(120);

--changeset fleetman:070-maintenance-workflow
ALTER TABLE fleet.maintenances
    ADD COLUMN IF NOT EXISTS status           VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    ADD COLUMN IF NOT EXISTS maintenance_type VARCHAR(20),
    ADD COLUMN IF NOT EXISTS urgent           BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS planned_at       TIMESTAMP,
    ADD COLUMN IF NOT EXISTS started_at       TIMESTAMP,
    ADD COLUMN IF NOT EXISTS completed_at     TIMESTAMP,
    ADD COLUMN IF NOT EXISTS cancelled_at     TIMESTAMP,
    ADD COLUMN IF NOT EXISTS cancel_reason    TEXT,
    ADD COLUMN IF NOT EXISTS requested_by     UUID;

-- Pas de bloc DO $$ : Liquibase découpe le SQL aux « ; » et couperait le bloc en deux.
ALTER TABLE fleet.maintenances DROP CONSTRAINT IF EXISTS maintenances_status_check;
ALTER TABLE fleet.maintenances ADD CONSTRAINT maintenances_status_check
    CHECK (status IN ('REQUESTED', 'PLANNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'));

CREATE INDEX IF NOT EXISTS idx_maintenances_status ON fleet.maintenances (status, vehicle_id);

-- Anciennes demandes de chauffeur : l'objet portait le type (PREVENTIVE / CORRECTIVE / EMERGENCY)
-- et l'urgence était glissée dans location_name. Elles redeviennent des demandes à traiter.
UPDATE fleet.maintenances
SET status = 'REQUESTED',
    maintenance_type = subject,
    urgent = (location_name = 'URGENT'),
    location_name = CASE WHEN location_name = 'URGENT' THEN NULL ELSE location_name END,
    requested_by = driver_id
WHERE subject IN ('PREVENTIVE', 'CORRECTIVE', 'EMERGENCY')
  AND driver_id IS NOT NULL
  AND cost IS NULL
  AND maintenance_type IS NULL;

UPDATE fleet.maintenances SET completed_at = date_time WHERE status = 'COMPLETED' AND completed_at IS NULL;

--changeset fleetman:070-notifications-unread-index
CREATE INDEX IF NOT EXISTS idx_notif_user_unread ON fleet.notifications (user_id) WHERE is_read = FALSE;
