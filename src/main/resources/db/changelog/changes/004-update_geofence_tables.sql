DROP TABLE IF EXISTS fleet.geofence_zones CASCADE;

CREATE TABLE fleet.geofence_zones (
    id UUID PRIMARY KEY, -- ID de la zone (Remote)
    manager_id UUID NOT NULL, -- Propriétaire de la zone (Fleet Manager)
    fleet_id UUID NULL REFERENCES fleet.fleets(id) ON DELETE SET NULL, -- Flotte associée (optionnel)
    zone_type VARCHAR(10) NOT NULL, -- Type de zone : "CIRCLE" ou "POLYGON"
    created_at TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_geofence_manager ON fleet.geofence_zones(manager_id);
CREATE INDEX idx_geofence_fleet_link ON fleet.geofence_zones(fleet_id);
CREATE INDEX idx_geofence_zone_type ON fleet.geofence_zones(zone_type);