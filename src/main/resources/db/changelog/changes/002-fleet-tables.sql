--liquibase formatted sql

--changeset gabriel:create-fleet-tables-v2 splitStatements:true

-- 1. NETTOYAGE PREALABLE (Au cas où)
-- On ne crée plus les types ENUM car ils posent problème avec R2DBC sans convertisseurs complexes.

-- 2. DONNÉES DE RÉFÉRENCE DYNAMIQUES
CREATE TABLE IF NOT EXISTS fleet.vehicle_types (
    id UUID PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL, 
    label VARCHAR(100) NOT NULL,      
    description TEXT
);

-- 3. ACTEURS (Extensions de public.users)
CREATE TABLE IF NOT EXISTS fleet.fleet_managers (
    user_id UUID PRIMARY KEY, 
    company_name VARCHAR(100)
);

-- 4. ORGANISATION
CREATE TABLE IF NOT EXISTS fleet.fleets (
  id UUID PRIMARY KEY,
  manager_id UUID NOT NULL REFERENCES fleet.fleet_managers(user_id),
  name VARCHAR(255) NOT NULL,
  phone_number VARCHAR(50),
  created_at TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS fleet.drivers (
  user_id UUID PRIMARY KEY, 
  fleet_id UUID REFERENCES fleet.fleets(id) ON DELETE SET NULL,
  licence_number VARCHAR(100) UNIQUE NOT NULL,
  status VARCHAR(50) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
  assigned_vehicle_id UUID, -- FK ajoutée plus tard pour éviter dépendance cyclique
  photo_url VARCHAR(255)
);

-- 5. GEOFENCING (Zones)
CREATE TABLE IF NOT EXISTS fleet.geofence_zones (
  id UUID PRIMARY KEY,
  fleet_id UUID NOT NULL REFERENCES fleet.fleets(id) ON DELETE CASCADE,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  type VARCHAR(50) DEFAULT 'POLYGON', -- POLYGON, CIRCLE
  radius NUMERIC,
  surface_area NUMERIC,
  perimeter NUMERIC
);

-- 6. VÉHICULES
CREATE TABLE IF NOT EXISTS fleet.vehicles (
  id UUID PRIMARY KEY,
  
  -- La flotte est OPTIONNELLE (Un véhicule indépendant a fleet_id = NULL)
  fleet_id UUID REFERENCES fleet.fleets(id) ON DELETE SET NULL,
  
  -- Le manager est OBLIGATOIRE (Un véhicule appartient toujours à quelqu'un)
  -- Et il pointe vers la table des managers
  manager_id UUID NOT NULL REFERENCES fleet.fleet_managers(user_id), 
  
  current_driver_id UUID, 
  
  vehicle_type_id UUID REFERENCES fleet.vehicle_types(id), 
  
  license_plate VARCHAR(50) UNIQUE NOT NULL,
  brand VARCHAR(100),
  model VARCHAR(100),
  manufacturing_year INT,
  color VARCHAR(50),
  status VARCHAR(50) DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'ON_TRIP', 'MAINTENANCE')),
 
  -- Stockage local des URLs
  photo_url VARCHAR(255),
  serial_number_photo_url VARCHAR(255),
  registration_photo_url VARCHAR(255)
);

-- 9. GÉOMÉTRIE / DÉTAILS
CREATE TABLE IF NOT EXISTS fleet.vehicle_illustration_images (
  id UUID PRIMARY KEY,
  vehicle_id UUID NOT NULL REFERENCES fleet.vehicles(id) ON DELETE CASCADE,
  image_path VARCHAR(255) NOT NULL
);

-- Optionnel mais recommandé pour la robustesse :
ALTER TABLE fleet.vehicles 
ADD CONSTRAINT fk_vehicle_current_driver 
FOREIGN KEY (current_driver_id) REFERENCES fleet.drivers(user_id) ON DELETE SET NULL;

-- Ajout de la FK manquante sur drivers
ALTER TABLE fleet.drivers 
ADD CONSTRAINT fk_driver_vehicle 
FOREIGN KEY (assigned_vehicle_id) REFERENCES fleet.vehicles(id) ON DELETE SET NULL;

-- 7. PARAMÈTRES VÉHICULES (1-1)
CREATE TABLE IF NOT EXISTS fleet.operational_parameters (
  id UUID PRIMARY KEY,
  vehicle_id UUID UNIQUE REFERENCES fleet.vehicles(id) ON DELETE CASCADE,
  current_location_point_id UUID, 
  status BOOLEAN DEFAULT true,
  current_speed NUMERIC,
  fuel_level VARCHAR(50),
  mileage NUMERIC,
  odometer_reading NUMERIC,
  bearing NUMERIC,
  timestamp TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS fleet.financial_parameters (
  id UUID PRIMARY KEY,
  vehicle_id UUID UNIQUE REFERENCES fleet.vehicles(id) ON DELETE CASCADE,
  insurance_number VARCHAR(100),
  insurance_expired_at DATE,
  registered_at DATE,
  purchased_at DATE,
  depreciation_rate INT,
  cost_per_km NUMERIC
);

CREATE TABLE IF NOT EXISTS fleet.maintenance_parameters (
  id UUID PRIMARY KEY,
  vehicle_id UUID UNIQUE REFERENCES fleet.vehicles(id) ON DELETE CASCADE,
  last_maintenance_at DATE,
  next_maintenance_at DATE,
  engine_status VARCHAR(50) DEFAULT 'OK' CHECK (engine_status IN ('OK', 'NEEDS_SERVICE', 'OUT_OF_SERVICE')),
  battery_health INT,
  maintenance_status VARCHAR(50) DEFAULT 'UP_TO_DATE' CHECK (maintenance_status IN ('UP_TO_DATE', 'PENDING', 'OVERDUE'))
);

-- 8. TRAJETS & ROUTES
CREATE TABLE IF NOT EXISTS fleet.trips (
  id UUID PRIMARY KEY,
  vehicle_id UUID REFERENCES fleet.vehicles(id) ON DELETE CASCADE,
  driver_id UUID REFERENCES fleet.drivers(user_id) ON DELETE SET NULL,
  start_date DATE NOT NULL,
  end_date DATE,
  start_time TIME NOT NULL,
  end_time TIME,
  status VARCHAR(50) DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED', 'ONGOING', 'COMPLETED', 'CANCELLED')),
  
  vehicle_type_id UUID REFERENCES fleet.vehicle_types(id), 
  
  distance_km NUMERIC,
  duration_minutes INT
);

-- 9. GÉOMÉTRIE
CREATE TABLE IF NOT EXISTS fleet.geofence_points (
  id UUID PRIMARY KEY,
  latitude NUMERIC NOT NULL,
  longitude NUMERIC NOT NULL
);

ALTER TABLE fleet.operational_parameters 
ADD CONSTRAINT fk_operational_point 
FOREIGN KEY (current_location_point_id) REFERENCES fleet.geofence_points(id) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS fleet.routes (
  id UUID PRIMARY KEY,
  trip_id UUID REFERENCES fleet.trips(id) ON DELETE CASCADE,
  start_point_id UUID REFERENCES fleet.geofence_points(id),
  end_point_id UUID REFERENCES fleet.geofence_points(id)
);

CREATE TABLE IF NOT EXISTS fleet.geofence_point_zone_linkages (
  point_id UUID REFERENCES fleet.geofence_points(id) ON DELETE CASCADE,
  zone_id UUID REFERENCES fleet.geofence_zones(id) ON DELETE CASCADE,
  vertex_order INT,
  PRIMARY KEY (point_id, zone_id)
);

-- 10. ÉVÉNEMENTS
CREATE TABLE IF NOT EXISTS fleet.geofence_events (
  id UUID PRIMARY KEY,
  vehicle_id UUID REFERENCES fleet.vehicles(id) ON DELETE CASCADE,
  zone_id UUID REFERENCES fleet.geofence_zones(id) ON DELETE SET NULL,
  type VARCHAR(50) CHECK (type IN ('ENTRY', 'EXIT')),
  timestamp TIMESTAMP DEFAULT now()
);