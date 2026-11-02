--liquibase formatted sql

-- Gestion documentaire, calquée sur le file-core du Kernel (document-hub / document-governance) :
--   * fleet.documents : une table unique (véhicule, chauffeur, gestionnaire) = le DocumentLink du
--     Kernel (cible + catégorie + fichier) plus la revue (DocumentReview) ;
--   * revue : dépôt EN ATTENTE, puis APPROUVÉ ou REJETÉ avec motif ; seul un document approuvé
--     compte pour la conformité ; le statut d'échéance se calcule à la lecture ;
--   * versions : un renouvellement crée une nouvelle version ; l'ancienne reste en vigueur jusqu'à
--     l'approbation de la nouvelle, puis passe à l'historique. Rien n'est effacé (archivage motivé) ;
--   * fleet.document_policies : pièces rendues obligatoires par l'entreprise (= politiques de
--     gouvernance du Kernel), en plus du socle légal fixé dans le code ;
--   * kernel_link_id : identifiant du DocumentLink quand le hub du Kernel sera branché.
-- Décision du 2026-11-02 : les documents sans fichier joint (aucune preuve) sont supprimés. Ceux
-- qui ont un fichier sont repris en attente de contrôle.

--changeset fleetman:059-documents
CREATE TABLE fleet.documents (
    id                  UUID PRIMARY KEY,
    target_type         VARCHAR(20)  NOT NULL CHECK (target_type IN ('VEHICLE', 'DRIVER', 'MANAGER')),
    target_id           UUID         NOT NULL,
    doc_type            VARCHAR(50)  NOT NULL,
    doc_number          VARCHAR(100),
    license_categories  VARCHAR(50),
    issuer              VARCHAR(255),
    issue_date          DATE,
    expiry_date         DATE,
    file_url            VARCHAR(500) NOT NULL,
    file_original_name  VARCHAR(255),
    file_mime_type      VARCHAR(100),
    file_size_bytes     BIGINT,
    kernel_file_id      UUID,
    kernel_link_id      UUID,
    review_status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING' CHECK (review_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    rejection_reason    TEXT,
    reviewed_by         UUID REFERENCES fleet.users(id) ON DELETE SET NULL,
    reviewed_at         TIMESTAMP,
    version             INTEGER      NOT NULL DEFAULT 1,
    is_current          BOOLEAN      NOT NULL DEFAULT FALSE,
    superseded_by       UUID REFERENCES fleet.documents(id) ON DELETE SET NULL,
    superseded_at       TIMESTAMP,
    archived_at         TIMESTAMP,
    archive_reason      TEXT,
    notes               TEXT,
    submitted_by        UUID REFERENCES fleet.users(id) ON DELETE SET NULL,
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP    NOT NULL,
    tenant_id           UUID
);

CREATE INDEX idx_documents_target ON fleet.documents (target_type, target_id, doc_type);
CREATE INDEX idx_documents_pending ON fleet.documents (review_status) WHERE review_status = 'PENDING' AND archived_at IS NULL;
CREATE INDEX idx_documents_expiry ON fleet.documents (expiry_date) WHERE is_current;
CREATE INDEX idx_documents_file ON fleet.documents (file_url);
-- Une seule version en vigueur par pièce (les « autres » documents peuvent être plusieurs).
CREATE UNIQUE INDEX uq_documents_current ON fleet.documents (target_type, target_id, doc_type)
    WHERE is_current AND doc_type <> 'OTHER';

INSERT INTO fleet.documents (id, target_type, target_id, doc_type, doc_number, issuer, issue_date, expiry_date,
                             file_url, file_original_name, file_mime_type, file_size_bytes, kernel_file_id,
                             notes, submitted_by, created_at, updated_at, tenant_id)
SELECT id, 'VEHICLE', vehicle_id, doc_type, doc_number, issuer, issue_date, expiry_date,
       file_url, file_original_name, file_mime_type, file_size_bytes, kernel_file_id,
       notes, uploaded_by, created_at, updated_at, tenant_id
FROM fleet.vehicle_documents
WHERE file_url IS NOT NULL AND file_url <> '';

INSERT INTO fleet.documents (id, target_type, target_id, doc_type, doc_number, license_categories, issuer, issue_date,
                             expiry_date, file_url, file_original_name, file_mime_type, file_size_bytes, kernel_file_id,
                             notes, submitted_by, created_at, updated_at, tenant_id)
SELECT id, 'DRIVER', driver_id, doc_type, doc_number, license_categories, issuer, issue_date,
       expiry_date, file_url, file_original_name, file_mime_type, file_size_bytes, kernel_file_id,
       notes, uploaded_by, created_at, updated_at, tenant_id
FROM fleet.driver_documents
WHERE file_url IS NOT NULL AND file_url <> '';

DROP TABLE fleet.vehicle_documents;
DROP TABLE fleet.driver_documents;

--changeset fleetman:059-document-policies
CREATE TABLE fleet.document_policies (
    company_id   UUID        NOT NULL REFERENCES fleet.companies(id) ON DELETE CASCADE,
    target_type  VARCHAR(20) NOT NULL CHECK (target_type IN ('VEHICLE', 'DRIVER', 'MANAGER')),
    doc_type     VARCHAR(50) NOT NULL,
    mandatory    BOOLEAN     NOT NULL,
    updated_by   UUID REFERENCES fleet.users(id) ON DELETE SET NULL,
    updated_at   TIMESTAMP   NOT NULL,
    PRIMARY KEY (company_id, target_type, doc_type)
);

--changeset fleetman:059-document-alerts
ALTER TABLE fleet.document_alerts DROP CONSTRAINT IF EXISTS document_alerts_document_type_check;
ALTER TABLE fleet.document_alerts ADD CONSTRAINT document_alerts_document_type_check
    CHECK (document_type IN ('VEHICLE', 'DRIVER', 'MANAGER'));
DELETE FROM fleet.document_alerts;
-- Une alerte de chaque palier par destinataire : la tâche quotidienne ne renvoie pas deux fois.
CREATE UNIQUE INDEX uq_document_alerts ON fleet.document_alerts (document_id, alert_type, recipient_id);
