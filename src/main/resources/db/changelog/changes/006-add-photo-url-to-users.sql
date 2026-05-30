--liquibase formatted sql
--changeset gabriel:add-photo-url-to-users
--comment: Ajout de la colonne photo_url pour le cache local

ALTER TABLE fleet.users ADD COLUMN IF NOT EXISTS photo_url VARCHAR(255);