--liquibase formatted sql
--changeset fleet-media:create-file-management-v1 splitStatements:true

-- ============================================================
--  Module Gestion des Fichiers & Médias
--  Couvre :
--    - Registre centralisé des fichiers uploadés (fleet.uploaded_files)
--    - Enrichissement de fleet.vehicle_illustration_images (métadonnées)
-- ============================================================

-- ─────────────────────────────────────────────────────────────
-- 1. TABLE PRINCIPALE : Registre centralisé des fichiers
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS fleet.uploaded_files (
    id                UUID        PRIMARY KEY,

    -- Informations du fichier
    original_name     VARCHAR(255) NOT NULL,
    stored_name       VARCHAR(255),
    storage_path      VARCHAR(500),
    public_url        VARCHAR(500),
    mime_type         VARCHAR(100),
    file_size_bytes   BIGINT       CHECK (file_size_bytes >= 0),

    -- Contexte métier : quelle entité ce fichier illustre / documente
    entity_type       VARCHAR(50) NOT NULL
                      CHECK (entity_type IN (
                          'VEHICLE',
                          'DRIVER',
                          'USER',
                          'VEHICLE_DOCUMENT',
                          'DRIVER_DOCUMENT'
                      )),
    entity_id         UUID        NOT NULL,

    -- Rôle fonctionnel du fichier
    file_category     VARCHAR(50) NOT NULL
                      CHECK (file_category IN (
                          'PROFILE_PHOTO',            -- Photo de profil (user / driver)
                          'VEHICLE_MAIN_PHOTO',        -- Photo principale du véhicule
                          'VEHICLE_ILLUSTRATION',      -- Photo d'illustration (galerie)
                          'VEHICLE_SERIAL_PHOTO',      -- Photo numéro de série (VIN)
                          'VEHICLE_REGISTRATION_PHOTO',-- Photo carte grise
                          'VEHICLE_DOCUMENT_FILE',     -- Fichier PDF d'un document véhicule
                          'DRIVER_DOCUMENT_FILE',      -- Fichier PDF d'un document conducteur
                          'OTHER'
                      )),

    -- Traçabilité
    uploaded_by       UUID,
    uploaded_at       TIMESTAMP   NOT NULL DEFAULT now(),

    -- Cycle de vie
    is_active         BOOLEAN     NOT NULL DEFAULT TRUE,
    deleted_at        TIMESTAMP,
    replaced_by       UUID        REFERENCES fleet.uploaded_files(id) ON DELETE SET NULL
);

-- Index pour retrouver les fichiers d'une entité
CREATE INDEX IF NOT EXISTS idx_files_entity
    ON fleet.uploaded_files(entity_type, entity_id, file_category)
    WHERE is_active = TRUE;

-- Index pour les purges (fichiers supprimés)
CREATE INDEX IF NOT EXISTS idx_files_deleted
    ON fleet.uploaded_files(deleted_at)
    WHERE is_active = FALSE;

-- Index uploader
CREATE INDEX IF NOT EXISTS idx_files_uploader
    ON fleet.uploaded_files(uploaded_by, uploaded_at DESC)
    WHERE uploaded_by IS NOT NULL;

-- ─────────────────────────────────────────────────────────────
-- 2. ENRICHISSEMENT : fleet.vehicle_illustration_images
--    Ajout des métadonnées manquantes
-- ─────────────────────────────────────────────────────────────
ALTER TABLE fleet.vehicle_illustration_images
    ADD COLUMN IF NOT EXISTS original_name    VARCHAR(255),
    ADD COLUMN IF NOT EXISTS mime_type        VARCHAR(100),
    ADD COLUMN IF NOT EXISTS file_size_bytes  BIGINT,
    ADD COLUMN IF NOT EXISTS sort_order       INT     NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS alt_text         VARCHAR(255),
    ADD COLUMN IF NOT EXISTS uploaded_by      UUID,
    ADD COLUMN IF NOT EXISTS uploaded_at      TIMESTAMP NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS is_active        BOOLEAN   NOT NULL DEFAULT TRUE;

-- Index galerie véhicule (ordre d'affichage)
CREATE INDEX IF NOT EXISTS idx_vii_vehicle_order
    ON fleet.vehicle_illustration_images(vehicle_id, sort_order)
    WHERE is_active = TRUE;

-- ─────────────────────────────────────────────────────────────
-- 3. ENRICHISSEMENT : fleet.vehicle_documents
--    Ajout des colonnes fichier manquantes
-- ─────────────────────────────────────────────────────────────
ALTER TABLE fleet.vehicle_documents
    ADD COLUMN IF NOT EXISTS file_original_name  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS file_mime_type      VARCHAR(100),
    ADD COLUMN IF NOT EXISTS file_size_bytes     BIGINT,
    ADD COLUMN IF NOT EXISTS uploaded_by         UUID,
    ADD COLUMN IF NOT EXISTS uploaded_at         TIMESTAMP;

-- ─────────────────────────────────────────────────────────────
-- 4. ENRICHISSEMENT : fleet.driver_documents
--    Ajout des colonnes fichier manquantes
-- ─────────────────────────────────────────────────────────────
ALTER TABLE fleet.driver_documents
    ADD COLUMN IF NOT EXISTS file_original_name  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS file_mime_type      VARCHAR(100),
    ADD COLUMN IF NOT EXISTS file_size_bytes     BIGINT,
    ADD COLUMN IF NOT EXISTS uploaded_by         UUID,
    ADD COLUMN IF NOT EXISTS uploaded_at         TIMESTAMP;

-- ─────────────────────────────────────────────────────────────
-- 5. VUE UTILITAIRE : tous les fichiers actifs avec leur entité
-- ─────────────────────────────────────────────────────────────
CREATE OR REPLACE VIEW fleet.v_active_files AS
SELECT
    uf.id,
    uf.original_name,
    uf.public_url,
    uf.mime_type,
    uf.file_size_bytes,
    uf.entity_type,
    uf.entity_id,
    uf.file_category,
    uf.uploaded_by,
    uf.uploaded_at,
    -- Nom de l'entité liée (résolution selon le type)
    CASE uf.entity_type
        WHEN 'VEHICLE' THEN (SELECT v.license_plate FROM fleet.vehicles v WHERE v.id = uf.entity_id)
        WHEN 'DRIVER'  THEN (SELECT u.first_name || ' ' || u.last_name FROM fleet.users u WHERE u.id = uf.entity_id)
        WHEN 'USER'    THEN (SELECT u.first_name || ' ' || u.last_name FROM fleet.users u WHERE u.id = uf.entity_id)
        ELSE NULL
    END AS entity_label
FROM fleet.uploaded_files uf
WHERE uf.is_active = TRUE;
