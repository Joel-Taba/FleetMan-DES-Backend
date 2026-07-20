--liquibase formatted sql
--changeset fleet-managers:fleet-manager-gallery-v1 splitStatements:true

-- Logo / galerie photo de l'organisation (distinct de la photo de profil
-- personnelle du gestionnaire, qui reste sur fleet.users.photo_url).
ALTER TABLE fleet.fleet_managers
    ADD COLUMN IF NOT EXISTS logo_url VARCHAR(500);

CREATE TABLE IF NOT EXISTS fleet.fleet_manager_gallery_images (
    id UUID PRIMARY KEY,
    manager_id UUID NOT NULL REFERENCES fleet.fleet_managers(user_id) ON DELETE CASCADE,
    image_path VARCHAR(500) NOT NULL
);
