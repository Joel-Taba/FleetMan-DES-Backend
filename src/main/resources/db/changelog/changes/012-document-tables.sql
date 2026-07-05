--liquibase formatted sql

--changeset fleet-extensions:create-document-tables-v1 splitStatements:true
-- ============================================================
-- MODULE 2 — DOCUMENTS LÉGAUX
-- Tables : vehicle_documents, driver_documents, document_alerts
-- Schema : fleet
-- Dependances : fleet.vehicles, fleet.drivers
-- ============================================================


-- ─────────────────────────────────────────────────────────────
-- 1. TABLE fleet.vehicle_documents
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS fleet.vehicle_documents (
    id              UUID PRIMARY KEY,
    vehicle_id      UUID NOT NULL REFERENCES fleet.vehicles(id) ON DELETE CASCADE,
    doc_type        VARCHAR(50) NOT NULL
                    CHECK (doc_type IN (
                        'INSURANCE','REGISTRATION','TECHNICAL_CONTROL',
                        'TAX_STICKER','TRANSPORT_PERMIT','OTHER'
                    )),
    doc_number      VARCHAR(100),
    issuer          VARCHAR(255),
    issue_date      DATE,
    expiry_date     DATE NOT NULL,
    file_url        VARCHAR(500),
    status          VARCHAR(30) NOT NULL DEFAULT 'VALID'
                    CHECK (status IN ('VALID','EXPIRING_SOON',
                                      'EXPIRED','PENDING_RENEWAL')),
    notes           TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_vdoc_vehicle
    ON fleet.vehicle_documents(vehicle_id);
CREATE INDEX IF NOT EXISTS idx_vdoc_expiry
    ON fleet.vehicle_documents(expiry_date, status);
CREATE INDEX IF NOT EXISTS idx_vdoc_status
    ON fleet.vehicle_documents(status)
    WHERE status IN ('EXPIRING_SOON','EXPIRED');

CREATE OR REPLACE TRIGGER trg_vehicle_documents_updated_at
    BEFORE UPDATE ON fleet.vehicle_documents
    FOR EACH ROW EXECUTE FUNCTION fleet.update_updated_at_column();


-- ─────────────────────────────────────────────────────────────
-- 2. TABLE fleet.driver_documents
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS fleet.driver_documents (
    id                  UUID PRIMARY KEY,
    driver_id           UUID NOT NULL REFERENCES fleet.drivers(user_id) ON DELETE CASCADE,
    doc_type            VARCHAR(50) NOT NULL
                        CHECK (doc_type IN (
                            'DRIVING_LICENSE','MEDICAL_CERT','PROFESSIONAL_CARD',
                            'WORK_CONTRACT','ID_CARD','OTHER'
                        )),
    doc_number          VARCHAR(100),
    license_categories  VARCHAR(50),
    issuer              VARCHAR(255),
    issue_date          DATE,
    expiry_date         DATE,
    file_url            VARCHAR(500),
    status              VARCHAR(30) NOT NULL DEFAULT 'VALID'
                        CHECK (status IN ('VALID','EXPIRING_SOON',
                                          'EXPIRED','PENDING_RENEWAL')),
    notes               TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ddoc_driver
    ON fleet.driver_documents(driver_id);
CREATE INDEX IF NOT EXISTS idx_ddoc_expiry
    ON fleet.driver_documents(expiry_date, status)
    WHERE expiry_date IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ddoc_status
    ON fleet.driver_documents(status)
    WHERE status IN ('EXPIRING_SOON','EXPIRED');

CREATE OR REPLACE TRIGGER trg_driver_documents_updated_at
    BEFORE UPDATE ON fleet.driver_documents
    FOR EACH ROW EXECUTE FUNCTION fleet.update_updated_at_column();


-- ─────────────────────────────────────────────────────────────
-- 3. TABLE fleet.document_alerts — Historique des alertes envoyees
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS fleet.document_alerts (
    id              UUID PRIMARY KEY,
    document_id     UUID NOT NULL,
    document_type   VARCHAR(20) NOT NULL CHECK (document_type IN ('VEHICLE','DRIVER')),
    alert_type      VARCHAR(10) NOT NULL CHECK (alert_type IN ('J30','J15','J7','EXPIRED')),
    sent_at         TIMESTAMP NOT NULL DEFAULT now(),
    recipient_id    UUID NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_doc_alerts_document
    ON fleet.document_alerts(document_id, alert_type);
CREATE INDEX IF NOT EXISTS idx_doc_alerts_sent
    ON fleet.document_alerts(sent_at DESC);
