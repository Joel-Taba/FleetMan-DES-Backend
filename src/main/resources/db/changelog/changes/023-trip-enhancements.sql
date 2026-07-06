--liquibase formatted sql
--changeset fleet-trip:023-trip-enhancements splitStatements:true

-- Coordonnées lieux (Leaflet / Nominatim)
ALTER TABLE fleet.trips
    ADD COLUMN IF NOT EXISTS departure_lat NUMERIC(10, 7),
    ADD COLUMN IF NOT EXISTS departure_lng NUMERIC(10, 7),
    ADD COLUMN IF NOT EXISTS return_lat NUMERIC(10, 7),
    ADD COLUMN IF NOT EXISTS return_lng NUMERIC(10, 7);

-- Devise mission (défaut FCFA)
ALTER TABLE fleet.trips
    ADD COLUMN IF NOT EXISTS mission_cost_currency VARCHAR(10) NOT NULL DEFAULT 'XAF';

-- Horodatage enregistrement départ
ALTER TABLE fleet.trips
    ADD COLUMN IF NOT EXISTS departure_registered_at TIMESTAMP;

-- Compléments conducteur en attente de validation manager
CREATE TABLE IF NOT EXISTS fleet.trip_mission_submissions (
    id              UUID PRIMARY KEY,
    trip_id         UUID NOT NULL REFERENCES fleet.trips(id) ON DELETE CASCADE,
    submitted_by    UUID NOT NULL,
    item_type       VARCHAR(20) NOT NULL
        CHECK (item_type IN ('PASSENGER', 'CARGO', 'OTHER')),
    description     TEXT,
    quantity        INT,
    weight          NUMERIC(10, 2),
    notes           TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    reviewed_by     UUID,
    reviewed_at     TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_trips_driver_active
    ON fleet.trips(driver_id, status)
    WHERE status IN ('DEPARTED', 'RETURNING');

CREATE INDEX IF NOT EXISTS idx_trips_vehicle_active
    ON fleet.trips(vehicle_id, status)
    WHERE status IN ('DEPARTED', 'RETURNING');

CREATE INDEX IF NOT EXISTS idx_trip_submissions_trip
    ON fleet.trip_mission_submissions(trip_id, status);
