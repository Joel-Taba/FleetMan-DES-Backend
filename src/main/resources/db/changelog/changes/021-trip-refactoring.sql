--liquibase formatted sql
--changeset fleet-trip:trip-code-seq splitStatements:true
CREATE SEQUENCE IF NOT EXISTS fleet.trip_code_seq START 1;

--changeset fleet-trip:trip-code-function splitStatements:false
CREATE OR REPLACE FUNCTION fleet.generate_trip_code()
RETURNS VARCHAR AS $$
BEGIN
    RETURN 'TRJ-' || TO_CHAR(NOW(), 'YYYY') || '-' || LPAD(nextval('fleet.trip_code_seq')::TEXT, 4, '0');
END;
$$ LANGUAGE plpgsql;

--changeset fleet-trip:trip-refactoring-v1 splitStatements:true
ALTER TABLE fleet.trips ADD COLUMN IF NOT EXISTS trip_code        VARCHAR(30) UNIQUE;
ALTER TABLE fleet.trips ADD COLUMN IF NOT EXISTS fleet_id         UUID REFERENCES fleet.fleets(id) ON DELETE SET NULL;
ALTER TABLE fleet.trips ADD COLUMN IF NOT EXISTS mission_object   VARCHAR(500);
ALTER TABLE fleet.trips ADD COLUMN IF NOT EXISTS mission_cost     NUMERIC(14,2);
ALTER TABLE fleet.trips ADD COLUMN IF NOT EXISTS rate_type        VARCHAR(20);
ALTER TABLE fleet.trips ADD COLUMN IF NOT EXISTS departure_location       VARCHAR(255);
ALTER TABLE fleet.trips ADD COLUMN IF NOT EXISTS departure_km_index       NUMERIC(10,2);
ALTER TABLE fleet.trips ADD COLUMN IF NOT EXISTS departure_fuel_index     NUMERIC(8,2);
ALTER TABLE fleet.trips ADD COLUMN IF NOT EXISTS return_location          VARCHAR(255);
ALTER TABLE fleet.trips ADD COLUMN IF NOT EXISTS return_km_index          NUMERIC(10,2);
ALTER TABLE fleet.trips ADD COLUMN IF NOT EXISTS return_fuel_index        NUMERIC(8,2);
ALTER TABLE fleet.trips ADD COLUMN IF NOT EXISTS return_registered_at     TIMESTAMP;
ALTER TABLE fleet.trips ADD COLUMN IF NOT EXISTS computed_distance_km     NUMERIC(10,2);
ALTER TABLE fleet.trips ADD COLUMN IF NOT EXISTS computed_fuel_consumed   NUMERIC(8,2);
ALTER TABLE fleet.trips ADD COLUMN IF NOT EXISTS scheduled_return_datetime TIMESTAMP;
ALTER TABLE fleet.trips ADD COLUMN IF NOT EXISTS cancel_reason            VARCHAR(500);
ALTER TABLE fleet.trips ADD COLUMN IF NOT EXISTS cancelled_at             TIMESTAMP;
ALTER TABLE fleet.trips ADD COLUMN IF NOT EXISTS created_by               UUID;

-- 4. Migrer les statuts existants : ONGOING → DEPARTED
UPDATE fleet.trips SET status = 'DEPARTED' WHERE status = 'ONGOING';

-- 5. Supprimer l'ancienne contrainte CHECK sur status
ALTER TABLE fleet.trips DROP CONSTRAINT IF EXISTS trips_status_check;

-- 6. Nouvelle contrainte avec les statuts enrichis
ALTER TABLE fleet.trips ADD CONSTRAINT trips_status_check
    CHECK (status IN ('SCHEDULED','DEPARTED','RETURNING','COMPLETED','CANCELLED'));

-- 7. Index utiles
CREATE INDEX IF NOT EXISTS idx_trips_code    ON fleet.trips(trip_code)            WHERE trip_code IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_trips_fleet   ON fleet.trips(fleet_id, status);
CREATE INDEX IF NOT EXISTS idx_trips_manager ON fleet.trips(created_by, status);

-- 8. Table fleet.trip_details
CREATE TABLE IF NOT EXISTS fleet.trip_details (
    id                  UUID PRIMARY KEY,
    trip_id             UUID NOT NULL REFERENCES fleet.trips(id) ON DELETE CASCADE,
    item_type           VARCHAR(20) NOT NULL CHECK (item_type IN ('PASSENGER','CARGO','OTHER')),
    description         VARCHAR(255),
    quantity            INT NOT NULL DEFAULT 0,
    weight              NUMERIC(10,2),
    departure_quantity  INT,
    return_quantity     INT,
    sort_order          INT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_trip_details_trip ON fleet.trip_details(trip_id);
