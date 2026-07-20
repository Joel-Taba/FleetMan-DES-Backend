--liquibase formatted sql
--changeset fleet-admin:fleet-manager-nullable-v1 splitStatements:true

-- Nouvelle logique métier : c'est l'administrateur qui crée les flottes puis
-- les assigne (une ou plusieurs) à un gestionnaire — une flotte peut donc
-- exister temporairement sans gestionnaire assigné, entre sa création et son
-- affectation.
ALTER TABLE fleet.fleets
    ALTER COLUMN manager_id DROP NOT NULL;
