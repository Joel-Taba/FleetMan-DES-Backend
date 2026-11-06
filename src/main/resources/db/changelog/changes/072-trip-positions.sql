--liquibase formatted sql

-- Suivi GPS en direct : le téléphone du chauffeur envoie sa position pendant un trajet en cours.
-- Historique borné (les 200 derniers points par trajet, purgés à l'écriture) et dernière position
-- connue, sur le trajet et sur le véhicule. Ces colonnes ne sont pas mappées par les entités :
-- un enregistrement du trajet ou du véhicule ne les écrase donc jamais.

--changeset fleetman:072-trip-positions
CREATE TABLE IF NOT EXISTS fleet.trip_positions (
    id          BIGSERIAL PRIMARY KEY,
    trip_id     UUID             NOT NULL REFERENCES fleet.trips(id) ON DELETE CASCADE,
    vehicle_id  UUID,
    driver_id   UUID,
    latitude    DOUBLE PRECISION NOT NULL,
    longitude   DOUBLE PRECISION NOT NULL,
    speed_kmh   DOUBLE PRECISION,
    accuracy_m  DOUBLE PRECISION,
    recorded_at TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_trip_positions_trip ON fleet.trip_positions (trip_id, recorded_at DESC);

ALTER TABLE fleet.trips ADD COLUMN IF NOT EXISTS last_lat DOUBLE PRECISION;
ALTER TABLE fleet.trips ADD COLUMN IF NOT EXISTS last_lng DOUBLE PRECISION;
ALTER TABLE fleet.trips ADD COLUMN IF NOT EXISTS last_speed DOUBLE PRECISION;
ALTER TABLE fleet.trips ADD COLUMN IF NOT EXISTS last_position_at TIMESTAMPTZ;

ALTER TABLE fleet.vehicles ADD COLUMN IF NOT EXISTS last_lat DOUBLE PRECISION;
ALTER TABLE fleet.vehicles ADD COLUMN IF NOT EXISTS last_lng DOUBLE PRECISION;
ALTER TABLE fleet.vehicles ADD COLUMN IF NOT EXISTS last_speed DOUBLE PRECISION;
ALTER TABLE fleet.vehicles ADD COLUMN IF NOT EXISTS last_position_at TIMESTAMPTZ;
ALTER TABLE fleet.vehicles ADD COLUMN IF NOT EXISTS last_position_trip_id UUID;
