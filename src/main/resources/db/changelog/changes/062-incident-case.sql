--liquibase formatted sql

-- Dossier d'incident : Déclaré → Pris en charge → En traitement → Résolu → Clôturé (ou Annulé).
--   * incident_cases : instruction du dossier (lieu, trajet, responsable, tiers, blessés, assurance,
--     coût estimé, remboursement, cause, action corrective, responsabilité, ordre de réparation) ;
--   * incident_events : historique de chaque étape ; incident_attachments : photos et pièces ;
--   * coûts réels : dépenses (source_id = l'incident) soumises au circuit d'approbation ;
--     incidents.cost devient la somme des coûts réels approuvés, l'ancien montant passe en estimation ;
--   * immobilisation : véhicule roulant ou non, décision du gestionnaire possible (motif tracé) ;
--   * plus de suppression : annulation motivée.

--changeset fleetman:062-incident-status
ALTER TABLE fleet.incidents DROP CONSTRAINT IF EXISTS incidents_status_check;
ALTER TABLE fleet.incidents ADD CONSTRAINT incidents_status_check
    CHECK (status IN ('REPORTED', 'ACKNOWLEDGED', 'UNDER_INVESTIGATION', 'RESOLVED', 'CLOSED', 'CANCELLED'));
ALTER TABLE fleet.incidents
    ADD COLUMN IF NOT EXISTS vehicle_drivable BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS immobilization_override BOOLEAN;

-- Le nom du chauffeur enregistré était son identifiant technique.
UPDATE fleet.incidents i
SET driver_full_name = NULLIF(TRIM(COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, '')), '')
FROM fleet.users u
WHERE u.id = i.driver_id AND (i.driver_full_name IS NULL OR i.driver_full_name ~ '^[0-9a-f]{8}-');

--changeset fleetman:062-incident-cases
CREATE TABLE fleet.incident_cases (
    incident_id           UUID PRIMARY KEY REFERENCES fleet.incidents(id) ON DELETE CASCADE,
    location_label        VARCHAR(255),
    trip_id               UUID REFERENCES fleet.trips(id) ON DELETE SET NULL,
    reported_by_id        UUID REFERENCES fleet.users(id) ON DELETE SET NULL,
    handler_id            UUID REFERENCES fleet.users(id) ON DELETE SET NULL,
    estimated_cost        NUMERIC(14,2) CHECK (estimated_cost >= 0),
    injuries              BOOLEAN NOT NULL DEFAULT FALSE,
    injuries_count        INTEGER CHECK (injuries_count >= 0),
    third_party_involved  BOOLEAN NOT NULL DEFAULT FALSE,
    third_party_name      VARCHAR(255),
    third_party_plate     VARCHAR(50),
    third_party_insurer   VARCHAR(255),
    third_party_contact   VARCHAR(255),
    insurance_status      VARCHAR(20) NOT NULL DEFAULT 'NONE'
        CHECK (insurance_status IN ('NONE', 'DECLARED', 'IN_PROGRESS', 'ACCEPTED', 'REFUSED')),
    insurer_name          VARCHAR(255),
    refund_amount         NUMERIC(14,2) CHECK (refund_amount >= 0),
    refund_date           DATE,
    root_cause            TEXT,
    corrective_action     TEXT,
    responsibility        VARCHAR(20) NOT NULL DEFAULT 'UNDETERMINED'
        CHECK (responsibility IN ('DRIVER', 'THIRD_PARTY', 'NONE', 'UNDETERMINED')),
    maintenance_id        UUID REFERENCES fleet.maintenances(id) ON DELETE SET NULL,
    acknowledged_at       TIMESTAMP,
    closed_at             TIMESTAMP,
    cancelled_at          TIMESTAMP,
    cancel_reason         TEXT,
    tenant_id             UUID
);
CREATE INDEX idx_incident_cases_responsibility ON fleet.incident_cases (responsibility);

-- Incidents existants : l'ancien montant est une estimation, la responsabilité reste à établir.
INSERT INTO fleet.incident_cases (incident_id, reported_by_id, estimated_cost, tenant_id)
SELECT id, created_by, cost, tenant_id FROM fleet.incidents;

--changeset fleetman:062-incident-events
CREATE TABLE fleet.incident_events (
    id          UUID PRIMARY KEY,
    incident_id UUID NOT NULL REFERENCES fleet.incidents(id) ON DELETE CASCADE,
    event_type  VARCHAR(30) NOT NULL,
    from_status VARCHAR(30),
    to_status   VARCHAR(30),
    actor_id    UUID REFERENCES fleet.users(id) ON DELETE SET NULL,
    note        TEXT,
    created_at  TIMESTAMP NOT NULL
);
CREATE INDEX idx_incident_events ON fleet.incident_events (incident_id, created_at);

INSERT INTO fleet.incident_events (id, incident_id, event_type, to_status, note, created_at)
SELECT gen_random_uuid(), id, 'DECLARED', 'REPORTED', 'Reconstitué à la mise en place du dossier', incident_date_time
FROM fleet.incidents;

CREATE TABLE fleet.incident_attachments (
    id           UUID PRIMARY KEY,
    incident_id  UUID NOT NULL REFERENCES fleet.incidents(id) ON DELETE CASCADE,
    kind         VARCHAR(20) NOT NULL CHECK (kind IN ('PHOTO', 'REPORT', 'INVOICE', 'OTHER')),
    file_url     VARCHAR(500) NOT NULL,
    file_name    VARCHAR(255),
    mime_type    VARCHAR(120),
    size_bytes   BIGINT,
    uploaded_by  UUID REFERENCES fleet.users(id) ON DELETE SET NULL,
    created_at   TIMESTAMP NOT NULL
);
CREATE INDEX idx_incident_attachments ON fleet.incident_attachments (incident_id);
CREATE INDEX idx_incident_attachments_file ON fleet.incident_attachments (file_url);
