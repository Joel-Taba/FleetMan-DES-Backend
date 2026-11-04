--liquibase formatted sql

-- Tournées multi-arrêts : un trajet dessert une suite ordonnée d'arrêts (livraison ou
-- ramassage). Chaque ligne du manifeste est rattachée à un arrêt ; chaque arrêt est servi
-- (arrivée, livraison avec réceptionnaire et preuve) ou en échec (motif). La destination du
-- trajet devient le dernier arrêt. Les trajets existants reçoivent un arrêt unique tiré de leur
-- destination, avec l'état correspondant à leur avancement.

--changeset fleetman:065-trip-stops
CREATE TABLE IF NOT EXISTS fleet.trip_stops (
    id              UUID PRIMARY KEY,
    trip_id         UUID NOT NULL REFERENCES fleet.trips(id) ON DELETE CASCADE,
    sequence        INTEGER NOT NULL,
    stop_type       VARCHAR(10) NOT NULL CHECK (stop_type IN ('DELIVERY', 'PICKUP')),
    location        VARCHAR(255) NOT NULL,
    latitude        NUMERIC(10,7),
    longitude       NUMERIC(10,7),
    contact_name    VARCHAR(120),
    contact_phone   VARCHAR(30),
    instructions    VARCHAR(500),
    planned_arrival TIMESTAMP,
    status          VARCHAR(10) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'ARRIVED', 'COMPLETED', 'FAILED', 'SKIPPED')),
    arrived_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    recipient_name  VARCHAR(120),
    proof_url       VARCHAR(500),
    notes           TEXT,
    failure_reason  VARCHAR(30),
    failure_notes   TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    tenant_id       UUID,
    CONSTRAINT uq_trip_stop_sequence UNIQUE (trip_id, sequence) DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX IF NOT EXISTS idx_trip_stops_trip ON fleet.trip_stops (trip_id, sequence);

--changeset fleetman:065-detail-stop
ALTER TABLE fleet.trip_details
    ADD COLUMN IF NOT EXISTS stop_id UUID REFERENCES fleet.trip_stops(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS shortage_reason VARCHAR(30);
ALTER TABLE fleet.trip_mission_submissions
    ADD COLUMN IF NOT EXISTS stop_id UUID REFERENCES fleet.trip_stops(id) ON DELETE SET NULL;

--changeset fleetman:065-backfill-stops
INSERT INTO fleet.trip_stops (id, trip_id, sequence, stop_type, location, latitude, longitude, status,
                              arrived_at, completed_at, notes, failure_notes, created_at, updated_at, tenant_id)
SELECT gen_random_uuid(), t.id, 1, 'DELIVERY',
       COALESCE(NULLIF(TRIM(t.destination_location), ''), NULLIF(TRIM(t.arrival_location), ''), NULLIF(TRIM(t.return_location), ''), 'Destination'),
       t.destination_lat, t.destination_lng,
       CASE
           WHEN t.status IN ('ARRIVED', 'COMPLETED') THEN 'COMPLETED'
           WHEN t.status IN ('CANCELLED', 'MISSED') THEN 'SKIPPED'
           ELSE 'PENDING'
       END,
       CASE WHEN t.status IN ('ARRIVED', 'COMPLETED') THEN t.arrived_at END,
       CASE WHEN t.status IN ('ARRIVED', 'COMPLETED') THEN COALESCE(t.arrived_at, t.end_date + COALESCE(t.end_time, TIME '00:00')) END,
       t.arrival_notes,
       CASE WHEN t.status IN ('CANCELLED', 'MISSED') THEN 'Trajet ' || LOWER(t.status) END,
       NOW(), NOW(), t.tenant_id
FROM fleet.trips t
WHERE NOT EXISTS (SELECT 1 FROM fleet.trip_stops s WHERE s.trip_id = t.id);

UPDATE fleet.trip_details d SET stop_id = s.id
FROM fleet.trip_stops s
WHERE s.trip_id = d.trip_id AND s.sequence = 1 AND d.stop_id IS NULL;
