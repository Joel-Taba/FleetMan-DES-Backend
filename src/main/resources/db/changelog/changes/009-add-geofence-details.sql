--liquibase formatted sql

--changeset gabriel:add-geofence-event-details
--comment: Ajout des détails (vitesse, durée) pour les alertes Geofence

ALTER TABLE fleet.geofence_events 
ADD COLUMN IF NOT EXISTS speed NUMERIC,
ADD COLUMN IF NOT EXISTS dwell_time_minutes INTEGER,
ADD COLUMN IF NOT EXISTS severity VARCHAR(20) DEFAULT 'INFO',
ADD COLUMN IF NOT EXISTS is_read BOOLEAN DEFAULT FALSE;