--liquibase formatted sql
--changeset gabriel:add-all-vehicle-resources-v2

-- 1. Constructeurs
CREATE TABLE IF NOT EXISTS fleet.manufacturers (
    id UUID PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    label VARCHAR(100) NOT NULL,
    description TEXT
);

-- 2. Marques commerciales
CREATE TABLE IF NOT EXISTS fleet.brands (
    id UUID PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    label VARCHAR(100) NOT NULL,
    description TEXT
);

-- 3. Modèles
CREATE TABLE IF NOT EXISTS fleet.vehicle_models (
    id UUID PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    label VARCHAR(100) NOT NULL,
    description TEXT
);

-- 4. Gabarits (Sizes)
CREATE TABLE IF NOT EXISTS fleet.vehicle_sizes (
    id UUID PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    label VARCHAR(100) NOT NULL,
    description TEXT
);

-- 5. Types d'usage (Usage Types)
CREATE TABLE IF NOT EXISTS fleet.usage_types (
    id UUID PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    label VARCHAR(100) NOT NULL,
    description TEXT
);

-- 6. Carburants (Fuel)
CREATE TABLE IF NOT EXISTS fleet.fuel_types (
    id UUID PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    label VARCHAR(100) NOT NULL,
    description TEXT
);

-- 7. Transmissions
CREATE TABLE IF NOT EXISTS fleet.transmission_types (
    id UUID PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    label VARCHAR(100) NOT NULL,
    description TEXT
);

-- 8. Couleurs
CREATE TABLE IF NOT EXISTS fleet.vehicle_colors (
    id UUID PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    label VARCHAR(100) NOT NULL,
    description TEXT
);

-- Indexation pour les lookups
CREATE INDEX idx_mfr_lookup ON fleet.manufacturers(code);
CREATE INDEX idx_brd_lookup ON fleet.brands(code);
CREATE INDEX idx_mod_lookup ON fleet.vehicle_models(code);
CREATE INDEX idx_size_lookup ON fleet.vehicle_sizes(code);
CREATE INDEX idx_usage_lookup ON fleet.usage_types(code);
CREATE INDEX idx_fuel_lookup ON fleet.fuel_types(code);
CREATE INDEX idx_trans_lookup ON fleet.transmission_types(code);
CREATE INDEX idx_color_lookup ON fleet.vehicle_colors(code);