--liquibase formatted sql
--changeset fleet-business:incidents-and-licenses-v1 splitStatements:true

-- 1. Catégorie de permis exigée par type de véhicule (classification CEMAC).
--    Un chauffeur ne peut être engagé (trajet, départ, affectation) que si un de ses permis
--    enregistrés, valide sur tout le créneau, couvre cette catégorie.
ALTER TABLE fleet.vehicle_types ADD COLUMN IF NOT EXISTS required_license_category VARCHAR(5);
UPDATE fleet.vehicle_types SET required_license_category = CASE upper(code)
        WHEN 'BIKE' THEN 'A' WHEN 'MOTO' THEN 'A'
        WHEN 'CAR' THEN 'B' WHEN 'SUV' THEN 'B' WHEN 'COUPE' THEN 'B' WHEN 'PICKUP' THEN 'B' WHEN 'VAN' THEN 'B'
        WHEN 'TRUCK' THEN 'C' WHEN 'TRACTOR' THEN 'C'
        WHEN 'BUS' THEN 'D' WHEN 'MINIBUS' THEN 'D'
        WHEN 'TRAILER' THEN 'E'
    END
WHERE required_license_category IS NULL;

-- 2. Incident grave (HIGH/CRITICAL) ouvert : le véhicule est immobilisé (MAINTENANCE) jusqu'à
--    la résolution. Ce drapeau indique que c'est (aussi) cet incident qui le retient : seule
--    sa résolution peut le libérer, jamais celle d'un incident étranger à l'immobilisation.
ALTER TABLE fleet.incidents ADD COLUMN IF NOT EXISTS vehicle_immobilized BOOLEAN NOT NULL DEFAULT false;

-- 3. Trajet en cours dont le véhicule est immobilisé : il reste ouvert, marqué interrompu,
--    et reprend au remplacement du véhicule ou à la résolution de l'incident.
ALTER TABLE fleet.trips ADD COLUMN IF NOT EXISTS interrupted_at TIMESTAMP;
ALTER TABLE fleet.trips ADD COLUMN IF NOT EXISTS resumed_at TIMESTAMP;
ALTER TABLE fleet.trips ADD COLUMN IF NOT EXISTS interruption_incident_id UUID;
