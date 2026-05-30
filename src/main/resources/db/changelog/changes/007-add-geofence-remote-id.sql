--liquibase formatted sql

--changeset gabriel:add-geofence-remote-id-to-vehicles
--comment: Ajout de l'ID technique du véhicule dans le système Geofence

ALTER TABLE fleet.vehicles 
ADD COLUMN geofence_remote_id VARCHAR(100);