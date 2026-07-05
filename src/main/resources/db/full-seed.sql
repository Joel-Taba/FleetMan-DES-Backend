-- ============================================================
--  FleetMan — Script de seed complet pour tests fonctionnels
--  Couvre les 42 tables du schéma fleet
--  Idempotent (ON CONFLICT DO NOTHING / DO UPDATE)
-- ============================================================

-- ─────────────────────────────────────────────────────────────
-- 0. NETTOYAGE (optionnel — décommenté si reset total souhaité)
-- ─────────────────────────────────────────────────────────────
-- TRUNCATE fleet.alert_events, fleet.alert_rules, fleet.maintenance_alerts,
--   fleet.maintenance_plans, fleet.driver_scores, fleet.budgets, fleet.expenses,
--   fleet.kpi_snapshots, fleet.document_alerts, fleet.driver_documents,
--   fleet.vehicle_documents, fleet.assignments, fleet.schedules,
--   fleet.fuel_recharges, fleet.incidents, fleet.maintenances,
--   fleet.geofence_events, fleet.geofence_point_zone_linkages, fleet.geofence_points,
--   fleet.geofence_zones, fleet.routes, fleet.trips,
--   fleet.notifications, fleet.notification_settings,
--   fleet.maintenance_parameters, fleet.financial_parameters, fleet.operational_parameters,
--   fleet.vehicle_illustration_images, fleet.vehicles, fleet.drivers, fleet.fleets,
--   fleet.fleet_managers, fleet.users,
--   fleet.vehicle_colors, fleet.transmission_types, fleet.fuel_types, fleet.usage_types,
--   fleet.vehicle_sizes, fleet.vehicle_models, fleet.brands, fleet.manufacturers,
--   fleet.vehicle_types CASCADE;

-- ═══════════════════════════════════════════════════════════════
-- 1. DONNÉES DE RÉFÉRENCE
-- ═══════════════════════════════════════════════════════════════

-- 1a. Types de véhicules
INSERT INTO fleet.vehicle_types (id, code, label, description) VALUES
  ('00000001-0000-4000-8000-000000000001', 'CAR',     'Voiture',    'Véhicule léger de tourisme'),
  ('00000001-0000-4000-8000-000000000002', 'TRUCK',   'Camion',     'Poids lourd / camion de livraison'),
  ('00000001-0000-4000-8000-000000000003', 'MINIBUS', 'Minibus',    'Transport de personnes ≤ 25 places'),
  ('00000001-0000-4000-8000-000000000004', 'VAN',     'Fourgon',    'Utilitaire léger'),
  ('00000001-0000-4000-8000-000000000005', 'MOTO',    'Moto',       'Deux roues motorisé')
ON CONFLICT (id) DO NOTHING;

-- 1b. Constructeurs
INSERT INTO fleet.manufacturers (id, code, label, description) VALUES
  ('00000002-0000-4000-8000-000000000001', 'TOYOTA',      'Toyota',      'Constructeur japonais'),
  ('00000002-0000-4000-8000-000000000002', 'MERCEDES',    'Mercedes-Benz','Constructeur allemand'),
  ('00000002-0000-4000-8000-000000000003', 'MITSUBISHI',  'Mitsubishi',  'Constructeur japonais'),
  ('00000002-0000-4000-8000-000000000004', 'RENAULT',     'Renault',     'Constructeur français'),
  ('00000002-0000-4000-8000-000000000005', 'FORD',        'Ford',        'Constructeur américain'),
  ('00000002-0000-4000-8000-000000000006', 'ISUZU',       'Isuzu',       'Constructeur japonais')
ON CONFLICT (id) DO NOTHING;

-- 1c. Marques
INSERT INTO fleet.brands (id, code, label, description) VALUES
  ('00000003-0000-4000-8000-000000000001', 'TOYOTA',      'Toyota',      'Marque japonaise'),
  ('00000003-0000-4000-8000-000000000002', 'MERCEDES',    'Mercedes',    'Marque allemande'),
  ('00000003-0000-4000-8000-000000000003', 'MITSUBISHI',  'Mitsubishi',  'Marque japonaise'),
  ('00000003-0000-4000-8000-000000000004', 'RENAULT',     'Renault',     'Marque française'),
  ('00000003-0000-4000-8000-000000000005', 'FORD',        'Ford',        'Marque américaine'),
  ('00000003-0000-4000-8000-000000000006', 'ISUZU',       'Isuzu',       'Marque japonaise')
ON CONFLICT (id) DO NOTHING;

-- 1d. Modèles
INSERT INTO fleet.vehicle_models (id, code, label, description) VALUES
  ('00000004-0000-4000-8000-000000000001', 'HILUX',    'Hilux',      'Toyota Hilux pick-up'),
  ('00000004-0000-4000-8000-000000000002', 'COROLLA',  'Corolla',    'Toyota Corolla berline'),
  ('00000004-0000-4000-8000-000000000003', 'ACTROS',   'Actros',     'Mercedes Actros poids lourd'),
  ('00000004-0000-4000-8000-000000000004', 'SPRINTER', 'Sprinter',   'Mercedes Sprinter fourgon'),
  ('00000004-0000-4000-8000-000000000005', 'L200',     'L200',       'Mitsubishi L200 pick-up'),
  ('00000004-0000-4000-8000-000000000006', 'MASTER',   'Master',     'Renault Master utilitaire'),
  ('00000004-0000-4000-8000-000000000007', 'TRANSIT',  'Transit',    'Ford Transit fourgon'),
  ('00000004-0000-4000-8000-000000000008', 'NPR',      'NPR',        'Isuzu NPR camion léger')
ON CONFLICT (id) DO NOTHING;

-- 1e. Gabarits
INSERT INTO fleet.vehicle_sizes (id, code, label, description) VALUES
  ('00000005-0000-4000-8000-000000000001', 'SMALL',  'Petit',  'Véhicule compact'),
  ('00000005-0000-4000-8000-000000000002', 'MEDIUM', 'Moyen',  'Véhicule intermédiaire'),
  ('00000005-0000-4000-8000-000000000003', 'LARGE',  'Grand',  'Grand véhicule / poids lourd')
ON CONFLICT (id) DO NOTHING;

-- 1f. Types d'usage
INSERT INTO fleet.usage_types (id, code, label, description) VALUES
  ('00000006-0000-4000-8000-000000000001', 'COMMERCIAL', 'Commercial',  'Usage professionnel / livraison'),
  ('00000006-0000-4000-8000-000000000002', 'PERSONAL',   'Personnel',   'Usage personnel'),
  ('00000006-0000-4000-8000-000000000003', 'MIXED',      'Mixte',       'Usage mixte pro/perso')
ON CONFLICT (id) DO NOTHING;

-- 1g. Types de carburant
INSERT INTO fleet.fuel_types (id, code, label, description) VALUES
  ('00000007-0000-4000-8000-000000000001', 'PETROL',   'Essence',     'Carburant essence'),
  ('00000007-0000-4000-8000-000000000002', 'DIESEL',   'Diesel',      'Gazole'),
  ('00000007-0000-4000-8000-000000000003', 'HYBRID',   'Hybride',     'Motorisation hybride'),
  ('00000007-0000-4000-8000-000000000004', 'ELECTRIC', 'Électrique',  'Motorisation 100% électrique')
ON CONFLICT (id) DO NOTHING;

-- 1h. Transmissions
INSERT INTO fleet.transmission_types (id, code, label, description) VALUES
  ('00000008-0000-4000-8000-000000000001', 'MANUAL',    'Manuelle',    'Boîte manuelle'),
  ('00000008-0000-4000-8000-000000000002', 'AUTOMATIC', 'Automatique', 'Boîte automatique'),
  ('00000008-0000-4000-8000-000000000003', 'CVT',       'CVT',         'Transmission à variation continue')
ON CONFLICT (id) DO NOTHING;

-- 1i. Couleurs
INSERT INTO fleet.vehicle_colors (id, code, label, description) VALUES
  ('00000009-0000-4000-8000-000000000001', 'BLANC',   'Blanc',   'Blanc uni'),
  ('00000009-0000-4000-8000-000000000002', 'NOIR',    'Noir',    'Noir'),
  ('00000009-0000-4000-8000-000000000003', 'ROUGE',   'Rouge',   'Rouge'),
  ('00000009-0000-4000-8000-000000000004', 'BLEU',    'Bleu',    'Bleu'),
  ('00000009-0000-4000-8000-000000000005', 'GRIS',    'Gris',    'Gris'),
  ('00000009-0000-4000-8000-000000000006', 'ARGENT',  'Argent',  'Argent métallisé'),
  ('00000009-0000-4000-8000-000000000007', 'VERT',    'Vert',    'Vert'),
  ('00000009-0000-4000-8000-000000000008', 'ORANGE',  'Orange',  'Orange')
ON CONFLICT (id) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════
-- 2. UTILISATEURS (fleet.users)
-- ═══════════════════════════════════════════════════════════════
INSERT INTO fleet.users (id, username, email, first_name, last_name, is_active, last_login_at) VALUES
  ('a0000001-0000-4000-8000-000000000001', 'superadmin',  'superadmin@fleetman.cm', 'Jean',    'Super',    true, now()),
  ('a0000002-0000-4000-8000-000000000002', 'fleetadmin',  'admin@fleetman.cm',      'Marie',   'Admin',    true, now()),
  ('a0000003-0000-4000-8000-000000000003', 'fleetmanager','manager@fleetman.cm',    'Paul',    'Manager',  true, now()),
  ('a0000004-0000-4000-8000-000000000004', 'fleetdriver', 'driver@fleetman.cm',     'André',   'Mbarga',   true, now()),
  ('a0000005-0000-4000-8000-000000000005', 'fleetdriver2','driver2@fleetman.cm',    'Sophie',  'Nguema',   true, now()),
  ('a0000006-0000-4000-8000-000000000006', 'fleetmanager2','manager2@fleetman.cm',  'Claire',  'Fouda',    true, now())
ON CONFLICT (id) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════
-- 3. GESTIONNAIRES DE FLOTTE (fleet.fleet_managers)
-- ═══════════════════════════════════════════════════════════════
INSERT INTO fleet.fleet_managers (user_id, company_name) VALUES
  ('a0000003-0000-4000-8000-000000000003', 'Transport Express CM'),
  ('a0000006-0000-4000-8000-000000000006', 'Logistics Douala SARL')
ON CONFLICT (user_id) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════
-- 4. FLOTTES (fleet.fleets)
-- ═══════════════════════════════════════════════════════════════
INSERT INTO fleet.fleets (id, manager_id, name, phone_number, created_at) VALUES
  ('b0000001-0000-4000-8000-000000000001', 'a0000003-0000-4000-8000-000000000003', 'Flotte Yaoundé',    '+237677000001', '2026-01-10 09:00:00'),
  ('b0000002-0000-4000-8000-000000000002', 'a0000003-0000-4000-8000-000000000003', 'Flotte Douala',     '+237677000002', '2026-01-15 10:00:00'),
  ('b0000003-0000-4000-8000-000000000003', 'a0000006-0000-4000-8000-000000000006', 'Flotte Bafoussam',  '+237699000003', '2026-02-01 08:00:00')
ON CONFLICT (id) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════
-- 5. CONDUCTEURS — insertion préalable sans véhicule assigné
-- ═══════════════════════════════════════════════════════════════
INSERT INTO fleet.drivers (user_id, fleet_id, licence_number, status, assigned_vehicle_id) VALUES
  ('a0000004-0000-4000-8000-000000000004', 'b0000001-0000-4000-8000-000000000001', 'B1234567CM', 'ACTIVE', NULL),
  ('a0000005-0000-4000-8000-000000000005', 'b0000002-0000-4000-8000-000000000002', 'C9876543CM', 'ACTIVE', NULL)
ON CONFLICT (user_id) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════
-- 6. VÉHICULES (fleet.vehicles)
-- ═══════════════════════════════════════════════════════════════
INSERT INTO fleet.vehicles (id, fleet_id, manager_id, vehicle_type_id, license_plate, brand, model, manufacturing_year, color, status) VALUES
  ('c0000001-0000-4000-8000-000000000001', 'b0000001-0000-4000-8000-000000000001', 'a0000003-0000-4000-8000-000000000003',
   '00000001-0000-4000-8000-000000000002', 'LT-892-CE', 'Toyota', 'Hilux',   2022, 'Bleu',  'ON_TRIP'),
  ('c0000002-0000-4000-8000-000000000002', 'b0000002-0000-4000-8000-000000000002', 'a0000003-0000-4000-8000-000000000003',
   '00000001-0000-4000-8000-000000000002', 'CE-456-AB', 'Mercedes', 'Actros', 2020, 'Blanc', 'AVAILABLE'),
  ('c0000003-0000-4000-8000-000000000003', 'b0000001-0000-4000-8000-000000000001', 'a0000003-0000-4000-8000-000000000003',
   '00000001-0000-4000-8000-000000000001', 'SW-123-DL', 'Toyota', 'Corolla', 2021, 'Gris',  'MAINTENANCE'),
  ('c0000004-0000-4000-8000-000000000004', 'b0000002-0000-4000-8000-000000000002', 'a0000003-0000-4000-8000-000000000003',
   '00000001-0000-4000-8000-000000000004', 'DL-001-YA', 'Renault', 'Master', 2023, 'Blanc', 'AVAILABLE'),
  ('c0000005-0000-4000-8000-000000000005', 'b0000003-0000-4000-8000-000000000003', 'a0000006-0000-4000-8000-000000000006',
   '00000001-0000-4000-8000-000000000003', 'BF-200-CM', 'Ford', 'Transit', 2022, 'Gris',  'AVAILABLE'),
  ('c0000006-0000-4000-8000-000000000006', 'b0000001-0000-4000-8000-000000000001', 'a0000003-0000-4000-8000-000000000003',
   '00000001-0000-4000-8000-000000000001', 'YA-777-LT', 'Mitsubishi', 'L200', 2021, 'Rouge', 'AVAILABLE')
ON CONFLICT (id) DO NOTHING;

-- Liaison véhicule ↔ conducteur
UPDATE fleet.drivers SET assigned_vehicle_id = 'c0000001-0000-4000-8000-000000000001'
  WHERE user_id = 'a0000004-0000-4000-8000-000000000004';
UPDATE fleet.drivers SET assigned_vehicle_id = 'c0000002-0000-4000-8000-000000000002'
  WHERE user_id = 'a0000005-0000-4000-8000-000000000005';

UPDATE fleet.vehicles SET current_driver_id = 'a0000004-0000-4000-8000-000000000004'
  WHERE id = 'c0000001-0000-4000-8000-000000000001';
UPDATE fleet.vehicles SET current_driver_id = 'a0000005-0000-4000-8000-000000000005'
  WHERE id = 'c0000002-0000-4000-8000-000000000002';

-- ═══════════════════════════════════════════════════════════════
-- 7. PARAMÈTRES VÉHICULES
-- ═══════════════════════════════════════════════════════════════

-- Paramètres opérationnels
INSERT INTO fleet.operational_parameters (id, vehicle_id, mileage, fuel_level, status, current_speed, odometer_reading) VALUES
  ('ca000001-0000-4000-8000-000000000001', 'c0000001-0000-4000-8000-000000000001', 87420,  '62',  true, 65, 87420),
  ('ca000002-0000-4000-8000-000000000002', 'c0000002-0000-4000-8000-000000000002', 120500, '45',  true, 0,  120500),
  ('ca000003-0000-4000-8000-000000000003', 'c0000003-0000-4000-8000-000000000003', 54300,  '20',  true, 0,  54300),
  ('ca000004-0000-4000-8000-000000000004', 'c0000004-0000-4000-8000-000000000004', 32100,  '80',  true, 0,  32100),
  ('ca000005-0000-4000-8000-000000000005', 'c0000005-0000-4000-8000-000000000005', 15200,  '90',  true, 0,  15200),
  ('ca000006-0000-4000-8000-000000000006', 'c0000006-0000-4000-8000-000000000006', 68000,  '55',  true, 0,  68000)
ON CONFLICT (id) DO NOTHING;

-- Paramètres financiers
INSERT INTO fleet.financial_parameters (id, vehicle_id, insurance_number, insurance_expired_at, cost_per_km, registered_at, purchased_at, depreciation_rate) VALUES
  ('cb000001-0000-4000-8000-000000000001', 'c0000001-0000-4000-8000-000000000001', 'ASS-2024-8891', '2026-12-15', 425, '2022-03-01', '2022-02-15', 15),
  ('cb000002-0000-4000-8000-000000000002', 'c0000002-0000-4000-8000-000000000002', 'ASS-2023-4412', '2026-06-30', 412, '2020-09-01', '2020-08-20', 12),
  ('cb000003-0000-4000-8000-000000000003', 'c0000003-0000-4000-8000-000000000003', 'ASS-2022-1120', '2026-06-14', 310, '2021-06-01', '2021-05-10', 15),
  ('cb000004-0000-4000-8000-000000000004', 'c0000004-0000-4000-8000-000000000004', 'ASS-2025-0071', '2027-01-20', 380, '2023-02-01', '2023-01-25', 10),
  ('cb000005-0000-4000-8000-000000000005', 'c0000005-0000-4000-8000-000000000005', 'ASS-2025-0099', '2027-03-10', 350, '2022-07-01', '2022-06-15', 12),
  ('cb000006-0000-4000-8000-000000000006', 'c0000006-0000-4000-8000-000000000006', 'ASS-2024-5500', '2026-11-30', 390, '2021-10-01', '2021-09-20', 14)
ON CONFLICT (id) DO NOTHING;

-- Paramètres de maintenance
INSERT INTO fleet.maintenance_parameters (id, vehicle_id, engine_status, battery_health, maintenance_status, last_maintenance_at, next_maintenance_at) VALUES
  ('cc000001-0000-4000-8000-000000000001', 'c0000001-0000-4000-8000-000000000001', 'OK',           88, 'UP_TO_DATE', '2026-03-15', '2026-09-15'),
  ('cc000002-0000-4000-8000-000000000002', 'c0000002-0000-4000-8000-000000000002', 'OK',           75, 'UP_TO_DATE', '2026-02-01', '2026-08-01'),
  ('cc000003-0000-4000-8000-000000000003', 'c0000003-0000-4000-8000-000000000003', 'NEEDS_SERVICE', 60, 'PENDING',   '2025-11-10', '2026-05-10'),
  ('cc000004-0000-4000-8000-000000000004', 'c0000004-0000-4000-8000-000000000004', 'OK',           95, 'UP_TO_DATE', '2026-04-20', '2026-10-20'),
  ('cc000005-0000-4000-8000-000000000005', 'c0000005-0000-4000-8000-000000000005', 'OK',           98, 'UP_TO_DATE', '2026-05-01', '2026-11-01'),
  ('cc000006-0000-4000-8000-000000000006', 'c0000006-0000-4000-8000-000000000006', 'NEEDS_SERVICE', 55, 'OVERDUE',  '2025-09-01', '2026-03-01')
ON CONFLICT (id) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════
-- 8. GÉOFENCES
-- ═══════════════════════════════════════════════════════════════

-- Zones
INSERT INTO fleet.geofence_zones (id, manager_id, fleet_id, zone_type, created_at) VALUES
  ('e0000001-0000-4000-8000-000000000001', 'a0000003-0000-4000-8000-000000000003', 'b0000001-0000-4000-8000-000000000001', 'CIRCLE',  now()),
  ('e0000002-0000-4000-8000-000000000002', 'a0000003-0000-4000-8000-000000000003', 'b0000002-0000-4000-8000-000000000002', 'CIRCLE',  now()),
  ('e0000003-0000-4000-8000-000000000003', 'a0000003-0000-4000-8000-000000000003', 'b0000001-0000-4000-8000-000000000001', 'POLYGON', now()),
  ('e0000004-0000-4000-8000-000000000004', 'a0000006-0000-4000-8000-000000000006', 'b0000003-0000-4000-8000-000000000003', 'CIRCLE',  now())
ON CONFLICT (id) DO NOTHING;

-- Points géographiques
INSERT INTO fleet.geofence_points (id, latitude, longitude) VALUES
  ('ep000001-0000-4000-8000-000000000001', 3.8480,  11.5021),   -- Yaoundé centre
  ('ep000002-0000-4000-8000-000000000002', 4.0511,  9.7679),    -- Douala port
  ('ep000003-0000-4000-8000-000000000003', 3.8671,  11.5220),   -- Yaoundé Mfandena
  ('ep000004-0000-4000-8000-000000000004', 5.4864,  10.4172)    -- Bafoussam
ON CONFLICT (id) DO NOTHING;

-- Événements géofence
INSERT INTO fleet.geofence_events (id, vehicle_id, zone_id, type, speed, severity, is_read) VALUES
  ('e1000001-0000-4000-8000-000000000001', 'c0000001-0000-4000-8000-000000000001', 'e0000001-0000-4000-8000-000000000001', 'EXIT',  82, 'WARNING', false),
  ('e1000002-0000-4000-8000-000000000002', 'c0000002-0000-4000-8000-000000000002', 'e0000002-0000-4000-8000-000000000002', 'ENTRY', 0,  'INFO',    true),
  ('e1000003-0000-4000-8000-000000000003', 'c0000003-0000-4000-8000-000000000003', 'e0000001-0000-4000-8000-000000000001', 'EXIT',  0,  'INFO',    true),
  ('e1000004-0000-4000-8000-000000000004', 'c0000004-0000-4000-8000-000000000004', 'e0000002-0000-4000-8000-000000000002', 'ENTRY', 45, 'INFO',    false)
ON CONFLICT (id) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════
-- 9. TRAJETS (fleet.trips)
-- ═══════════════════════════════════════════════════════════════
INSERT INTO fleet.trips (id, vehicle_id, driver_id, start_date, end_date, start_time, end_time, status, distance_km, duration_minutes) VALUES
  ('d0000001-0000-4000-8000-000000000001', 'c0000001-0000-4000-8000-000000000001', 'a0000004-0000-4000-8000-000000000004',
   '2026-05-28', '2026-05-28', '07:30', '11:45', 'COMPLETED', 148, 255),
  ('d0000002-0000-4000-8000-000000000002', 'c0000003-0000-4000-8000-000000000003', 'a0000004-0000-4000-8000-000000000004',
   '2026-06-02', '2026-06-02', '09:00', '13:30', 'COMPLETED', 96,  270),
  ('d0000003-0000-4000-8000-000000000003', 'c0000001-0000-4000-8000-000000000001', 'a0000004-0000-4000-8000-000000000004',
   '2026-06-06', NULL,          '10:30', NULL,    'ONGOING',   NULL, NULL),
  ('d0000004-0000-4000-8000-000000000004', 'c0000002-0000-4000-8000-000000000002', 'a0000005-0000-4000-8000-000000000005',
   '2026-06-04', '2026-06-04', '06:00', '14:30', 'COMPLETED', 312, 510),
  ('d0000005-0000-4000-8000-000000000005', 'c0000004-0000-4000-8000-000000000004', 'a0000005-0000-4000-8000-000000000005',
   '2026-06-05', '2026-06-05', '08:00', '12:15', 'COMPLETED', 89,  255),
  ('d0000006-0000-4000-8000-000000000006', 'c0000006-0000-4000-8000-000000000006', 'a0000004-0000-4000-8000-000000000004',
   '2026-06-10', NULL,          '07:00', NULL,    'SCHEDULED', NULL, NULL),
  ('d0000007-0000-4000-8000-000000000007', 'c0000001-0000-4000-8000-000000000001', 'a0000004-0000-4000-8000-000000000004',
   '2026-05-15', '2026-05-15', '14:00', '17:00', 'COMPLETED', 55,  180),
  ('d0000008-0000-4000-8000-000000000008', 'c0000002-0000-4000-8000-000000000002', 'a0000005-0000-4000-8000-000000000005',
   '2026-06-07', '2026-06-07', '09:30', '16:00', 'COMPLETED', 204, 390)
ON CONFLICT (id) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════
-- 10. OPÉRATIONS TERRAIN
-- ═══════════════════════════════════════════════════════════════

-- Maintenances
INSERT INTO fleet.maintenances (id, subject, cost, date_time, report, vehicle_id, vehicle_registration, driver_id, driver_full_name, location_name) VALUES
  ('f1000001-0000-4000-8000-000000000001', 'Vidange + remplacement filtres',  85000,  '2026-06-03 09:00:00', 'Vidange 5W40 effectuée, filtre huile et filtre air remplacés. RAS.',   'c0000003-0000-4000-8000-000000000003', 'SW-123-DL', 'a0000004-0000-4000-8000-000000000004', 'André Mbarga',   'Garage Auto Plus Yaoundé'),
  ('f1000004-0000-4000-8000-000000000004', 'Remplacement pneus avant',        120000, '2026-05-20 14:00:00', 'Deux pneus avant usés remplacés par Michelin 205/65R15.',             'c0000002-0000-4000-8000-000000000002', 'CE-456-AB', 'a0000005-0000-4000-8000-000000000005', 'Sophie Nguema',  'Garage Douala Centre'),
  ('f1000005-0000-4000-8000-000000000005', 'Révision 80 000 km',              250000, '2026-03-15 08:00:00', 'Révision complète : plaquettes, bougies, courroie de distribution.', 'c0000001-0000-4000-8000-000000000001', 'LT-892-CE', 'a0000004-0000-4000-8000-000000000004', 'André Mbarga',   'Concessionnaire Toyota Yaoundé'),
  ('f1000006-0000-4000-8000-000000000006', 'Réparation climatisation',        45000,  '2026-04-10 10:00:00', 'Recharge gaz climatisation + remplacement filtre habitacle.',        'c0000004-0000-4000-8000-000000000004', 'DL-001-YA', NULL, NULL, 'Froid Expert Douala')
ON CONFLICT (id) DO NOTHING;

-- Incidents
INSERT INTO fleet.incidents (id, type, description, severity, incident_date_time, cost, status, vehicle_id, vehicle_registration, driver_id, driver_full_name, reported_by, longitude, latitude) VALUES
  ('f1000002-0000-4000-8000-000000000002', 'ACCIDENT',          'Rayure latérale dans parking Carrefour Bastos',    'MEDIUM',   '2026-06-01 11:30:00', 15000,  'REPORTED',               'c0000001-0000-4000-8000-000000000001', 'LT-892-CE', 'a0000004-0000-4000-8000-000000000004', 'André Mbarga',  'André Mbarga',  11.5021, 3.8480),
  ('f1000007-0000-4000-8000-000000000007', 'BREAKDOWN',         'Crevaison pneu arrière gauche sur l''autoroute',   'LOW',      '2026-05-29 08:45:00', 8000,   'RESOLVED',               'c0000002-0000-4000-8000-000000000002', 'CE-456-AB', 'a0000005-0000-4000-8000-000000000005', 'Sophie Nguema', 'Sophie Nguema',  9.7679, 4.0511),
  ('f1000008-0000-4000-8000-000000000008', 'TRAFFIC_VIOLATION', 'Excès de vitesse sur boulevard de la Réunification','LOW',     '2026-05-25 14:20:00', 25000,  'CLOSED',                 'c0000003-0000-4000-8000-000000000003', 'SW-123-DL', 'a0000004-0000-4000-8000-000000000004', 'André Mbarga',  'Police routière',11.4878, 3.8480),
  ('f1000009-0000-4000-8000-000000000009', 'ACCIDENT',          'Collision légère à un carrefour, dommages mineurs', 'HIGH',    '2026-06-05 17:00:00', 180000, 'UNDER_INVESTIGATION',    'c0000006-0000-4000-8000-000000000006', 'YA-777-LT', 'a0000004-0000-4000-8000-000000000004', 'André Mbarga',  'André Mbarga',  11.5200, 3.8671)
ON CONFLICT (id) DO NOTHING;

-- Recharges carburant
INSERT INTO fleet.fuel_recharges (id, quantity, price, recharge_date_time, station_name, vehicle_id, vehicle_registration, driver_id, driver_full_name) VALUES
  ('f1000003-0000-4000-8000-000000000003', 65,  42250, '2026-06-01 07:00:00', 'TOTAL',    'c0000001-0000-4000-8000-000000000001', 'LT-892-CE', 'a0000004-0000-4000-8000-000000000004', 'André Mbarga'),
  ('f1000010-0000-4000-8000-000000000010', 80,  52000, '2026-05-30 16:30:00', 'SHELL',    'c0000002-0000-4000-8000-000000000002', 'CE-456-AB', 'a0000005-0000-4000-8000-000000000005', 'Sophie Nguema'),
  ('f1000011-0000-4000-8000-000000000011', 45,  29250, '2026-06-03 08:15:00', 'OILIBYA',  'c0000003-0000-4000-8000-000000000003', 'SW-123-DL', 'a0000004-0000-4000-8000-000000000004', 'André Mbarga'),
  ('f1000012-0000-4000-8000-000000000012', 55,  35750, '2026-06-05 11:00:00', 'TOTAL',    'c0000004-0000-4000-8000-000000000004', 'DL-001-YA', 'a0000005-0000-4000-8000-000000000005', 'Sophie Nguema'),
  ('f1000013-0000-4000-8000-000000000013', 70,  45500, '2026-06-06 06:30:00', 'SHELL',    'c0000001-0000-4000-8000-000000000001', 'LT-892-CE', 'a0000004-0000-4000-8000-000000000004', 'André Mbarga'),
  ('f1000014-0000-4000-8000-000000000014', 40,  26000, '2026-06-04 09:00:00', 'CAMRAIL',  'c0000006-0000-4000-8000-000000000006', 'YA-777-LT', 'a0000004-0000-4000-8000-000000000004', 'André Mbarga')
ON CONFLICT (id) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════
-- 11. DOCUMENTS
-- ═══════════════════════════════════════════════════════════════

-- Documents véhicules
INSERT INTO fleet.vehicle_documents (id, vehicle_id, doc_type, doc_number, issuer, issue_date, expiry_date, status, notes) VALUES
  ('f2000001-0000-4000-8000-000000000001', 'c0000001-0000-4000-8000-000000000001', 'INSURANCE',         'ASS-2024-8891',  'Assurance Chanas',        '2024-12-15', '2026-12-15', 'VALID',          'Tous risques'),
  ('f2000002-0000-4000-8000-000000000002', 'c0000002-0000-4000-8000-000000000002', 'TECHNICAL_CONTROL', 'VT-2025-112',    'Centre Contrôle Douala',  '2025-06-20', '2026-06-30', 'EXPIRING_SOON',  'Contrôle OK - renouveler avant fin juin'),
  ('f2000004-0000-4000-8000-000000000004', 'c0000003-0000-4000-8000-000000000003', 'INSURANCE',         'ASS-2022-1120',  'AXA Cameroun',            '2022-06-01', '2026-06-14', 'EXPIRING_SOON',  'À renouveler de toute urgence'),
  ('f2000005-0000-4000-8000-000000000005', 'c0000004-0000-4000-8000-000000000004', 'REGISTRATION',      'CE-REG-4411',    'DGTCFM',                  '2023-02-01', '2028-02-01', 'VALID',          NULL),
  ('f2000006-0000-4000-8000-000000000006', 'c0000001-0000-4000-8000-000000000001', 'TECHNICAL_CONTROL', 'VT-2024-887',    'Centre Contrôle Yaoundé', '2024-09-01', '2027-09-01', 'VALID',          NULL),
  ('f2000007-0000-4000-8000-000000000007', 'c0000005-0000-4000-8000-000000000005', 'INSURANCE',         'ASS-2025-0099',  'Activa Insurance',        '2025-03-10', '2027-03-10', 'VALID',          NULL),
  ('f2000008-0000-4000-8000-000000000008', 'c0000006-0000-4000-8000-000000000006', 'TRANSPORT_PERMIT',  'TP-2024-0441',   'MINT Cameroun',           '2024-01-01', '2026-05-10', 'EXPIRED',        'Permis de transport expiré !'),
  ('f2000009-0000-4000-8000-000000000009', 'c0000002-0000-4000-8000-000000000002', 'TAX_STICKER',       'VS-2026-CE456',  'DGI Douala',              '2026-01-01', '2026-12-31', 'VALID',          NULL)
ON CONFLICT (id) DO NOTHING;

-- Documents conducteurs
INSERT INTO fleet.driver_documents (id, driver_id, doc_type, doc_number, license_categories, issuer, issue_date, expiry_date, status, notes) VALUES
  ('f2000003-0000-4000-8000-000000000003', 'a0000004-0000-4000-8000-000000000004', 'DRIVING_LICENSE',  'B1234567CM', 'B, C, D',   'Préfecture Yaoundé',  '2017-03-10', '2027-03-10', 'VALID',         NULL),
  ('f2000010-0000-4000-8000-000000000010', 'a0000004-0000-4000-8000-000000000004', 'MEDICAL_CERT',     'MC-2025-789', NULL,        'Hôpital Central YDE', '2025-01-15', '2026-01-15', 'EXPIRED',       'Certificat médical expiré — à renouveler'),
  ('f2000011-0000-4000-8000-000000000011', 'a0000005-0000-4000-8000-000000000005', 'DRIVING_LICENSE',  'C9876543CM', 'B, C',      'Préfecture Douala',   '2020-07-22', '2030-07-22', 'VALID',         NULL),
  ('f2000012-0000-4000-8000-000000000012', 'a0000005-0000-4000-8000-000000000005', 'PROFESSIONAL_CARD','PR-2024-112', NULL,        'CNPS Douala',         '2024-06-01', '2026-06-30', 'EXPIRING_SOON', 'Carte pro à renouveler avant fin juin')
ON CONFLICT (id) DO NOTHING;

-- Alertes documents
INSERT INTO fleet.document_alerts (id, document_id, document_type, alert_type, sent_at, recipient_id) VALUES
  ('fa000001-0000-4000-8000-000000000001', 'f2000002-0000-4000-8000-000000000002', 'VEHICLE', 'J30', '2026-05-31 08:00:00', 'a0000003-0000-4000-8000-000000000003'),
  ('fa000002-0000-4000-8000-000000000002', 'f2000004-0000-4000-8000-000000000004', 'VEHICLE', 'J7',  '2026-06-07 08:00:00', 'a0000003-0000-4000-8000-000000000003'),
  ('fa000003-0000-4000-8000-000000000003', 'f2000008-0000-4000-8000-000000000008', 'VEHICLE', 'EXPIRED', '2026-05-11 08:00:00', 'a0000003-0000-4000-8000-000000000003'),
  ('fa000004-0000-4000-8000-000000000004', 'f2000010-0000-4000-8000-000000000010', 'DRIVER',  'EXPIRED', '2026-01-16 08:00:00', 'a0000003-0000-4000-8000-000000000003')
ON CONFLICT (id) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════
-- 12. PLANNINGS & AFFECTATIONS
-- ═══════════════════════════════════════════════════════════════

-- Plannings
INSERT INTO fleet.schedules (id, fleet_id, manager_id, title, period_type, start_date, end_date, status, notes) VALUES
  ('f3000001-0000-4000-8000-000000000001', 'b0000001-0000-4000-8000-000000000001', 'a0000003-0000-4000-8000-000000000003',
   'Planning semaine 10-16 Juin 2026', 'WEEKLY', '2026-06-10', '2026-06-16', 'PUBLISHED', 'Semaine normale — tous véhicules opérationnels'),
  ('f3000002-0000-4000-8000-000000000002', 'b0000002-0000-4000-8000-000000000002', 'a0000003-0000-4000-8000-000000000003',
   'Brouillon Juillet Douala', 'MONTHLY', '2026-07-01', '2026-07-31', 'DRAFT', 'En cours de préparation'),
  ('f3000005-0000-4000-8000-000000000005', 'b0000001-0000-4000-8000-000000000001', 'a0000003-0000-4000-8000-000000000003',
   'Planning semaine 3-9 Juin 2026', 'WEEKLY', '2026-06-03', '2026-06-09', 'ARCHIVED', 'Semaine passée — archivée'),
  ('f3000006-0000-4000-8000-000000000006', 'b0000003-0000-4000-8000-000000000003', 'a0000006-0000-4000-8000-000000000006',
   'Planning Bafoussam Juin', 'WEEKLY', '2026-06-10', '2026-06-16', 'PUBLISHED', 'Tournée régionale ouest')
ON CONFLICT (id) DO NOTHING;

-- Affectations
INSERT INTO fleet.assignments (id, schedule_id, fleet_id, vehicle_id, driver_id, start_datetime, end_datetime, status, estimated_km, start_location, end_location, notes) VALUES
  ('f3000003-0000-4000-8000-000000000003', 'f3000001-0000-4000-8000-000000000001', 'b0000001-0000-4000-8000-000000000001',
   'c0000001-0000-4000-8000-000000000001', 'a0000004-0000-4000-8000-000000000004',
   '2026-06-10 07:00:00', '2026-06-10 12:00:00', 'PENDING', 120, 'Yaoundé Centre', 'Mbalmayo', NULL),
  ('f3000004-0000-4000-8000-000000000004', 'f3000001-0000-4000-8000-000000000001', 'b0000001-0000-4000-8000-000000000001',
   'c0000006-0000-4000-8000-000000000006', 'a0000004-0000-4000-8000-000000000004',
   '2026-06-11 14:00:00', '2026-06-11 18:00:00', 'PENDING', 85, 'Yaoundé Mfandena', 'Obala', NULL),
  ('f3000007-0000-4000-8000-000000000007', 'f3000005-0000-4000-8000-000000000005', 'b0000001-0000-4000-8000-000000000001',
   'c0000001-0000-4000-8000-000000000001', 'a0000004-0000-4000-8000-000000000004',
   '2026-06-04 08:00:00', '2026-06-04 12:00:00', 'COMPLETED', 120, 'Yaoundé', 'Mbalmayo', 'Livraison effectuée sans incident'),
  ('f3000008-0000-4000-8000-000000000008', 'f3000001-0000-4000-8000-000000000001', 'b0000001-0000-4000-8000-000000000001',
   'c0000001-0000-4000-8000-000000000001', 'a0000004-0000-4000-8000-000000000004',
   '2026-06-12 06:00:00', '2026-06-12 18:00:00', 'PENDING', 310, 'Yaoundé', 'Douala', 'Liaison Yaoundé-Douala'),
  ('f3000009-0000-4000-8000-000000000009', 'f3000002-0000-4000-8000-000000000002', 'b0000002-0000-4000-8000-000000000002',
   'c0000002-0000-4000-8000-000000000002', 'a0000005-0000-4000-8000-000000000005',
   '2026-07-01 06:00:00', '2026-07-01 16:00:00', 'PENDING', 250, 'Douala Port', 'Bafoussam', 'Transport marchandises')
ON CONFLICT (id) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════
-- 13. KPIs
-- ═══════════════════════════════════════════════════════════════
INSERT INTO fleet.kpi_snapshots (id, fleet_id, entity_type, entity_id, period_type, period_start, period_end,
  total_km, total_trips, total_driving_hours, availability_rate,
  total_fuel_cost, total_fuel_liters, total_maintenance_cost, total_incident_cost,
  cost_per_km, fuel_per_100km, total_incidents, incident_rate, avg_driver_score, doc_compliance_rate) VALUES
  -- Flotte Yaoundé — semaine en cours
  ('f4000001-0000-4000-8000-000000000001', 'b0000001-0000-4000-8000-000000000001', 'FLEET',   'b0000001-0000-4000-8000-000000000001',
   'WEEKLY',  '2026-06-01', '2026-06-07', 12450, 48, 186.5, 87.5, 812500, 1625, 335000, 15000, 425, 13.1, 2, 0.042, 92.0, 88.5),
  -- Flotte Yaoundé — mois de mai
  ('f4000002-0000-4000-8000-000000000002', 'b0000001-0000-4000-8000-000000000001', 'FLEET',   'b0000001-0000-4000-8000-000000000001',
   'MONTHLY', '2026-05-01', '2026-05-31', 48200, 185, 720, 85.0, 3120000, 6240, 1250000, 55000, 418, 12.9, 5, 0.027, 89.5, 82.0),
  -- Véhicule LT-892-CE — mois en cours
  ('f4000003-0000-4000-8000-000000000003', 'b0000001-0000-4000-8000-000000000001', 'VEHICLE', 'c0000001-0000-4000-8000-000000000001',
   'MONTHLY', '2026-06-01', '2026-06-30', 4280, 18, 64, 91.0, 278200, 556.4, 0, 15000, 431, 13.0, 1, 0.056, NULL, NULL),
  -- Conducteur André — mois en cours
  ('f4000004-0000-4000-8000-000000000004', 'b0000001-0000-4000-8000-000000000001', 'DRIVER',  'a0000004-0000-4000-8000-000000000004',
   'MONTHLY', '2026-06-01', '2026-06-30', 2850, 12, 42.5, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, 0.083, 89.0, NULL),
  -- Flotte Douala — semaine
  ('f4000005-0000-4000-8000-000000000005', 'b0000002-0000-4000-8000-000000000002', 'FLEET',   'b0000002-0000-4000-8000-000000000002',
   'WEEKLY',  '2026-06-01', '2026-06-07', 6240, 22, 93, 90.0, 405600, 811.2, 52000, 8000, 412, 13.0, 1, 0.045, 94.0, 90.0),
  -- Conducteur Sophie — mensuel
  ('f4000006-0000-4000-8000-000000000006', 'b0000002-0000-4000-8000-000000000002', 'DRIVER',  'a0000005-0000-4000-8000-000000000005',
   'MONTHLY', '2026-06-01', '2026-06-30', 3100, 14, 46, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 97.0, NULL)
ON CONFLICT (entity_type, entity_id, period_type, period_start) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════
-- 14. BUDGETS & DÉPENSES
-- ═══════════════════════════════════════════════════════════════

-- Budgets
INSERT INTO fleet.budgets (id, scope, entity_id, manager_id, budget_month, amount, consumed, alert_level, notes) VALUES
  ('f5000001-0000-4000-8000-000000000001', 'FLEET',   'b0000001-0000-4000-8000-000000000001', 'a0000003-0000-4000-8000-000000000003', '2026-06-01', 2500000, 1024000, 'NORMAL',  'Budget mensuel Flotte Yaoundé'),
  ('f5000003-0000-4000-8000-000000000003', 'FLEET',   'b0000002-0000-4000-8000-000000000002', 'a0000003-0000-4000-8000-000000000003', '2026-06-01', 1800000, 1530000, 'WARNING', 'Budget mensuel Flotte Douala — proche limite'),
  ('f5000004-0000-4000-8000-000000000004', 'VEHICLE', 'c0000001-0000-4000-8000-000000000001', 'a0000003-0000-4000-8000-000000000003', '2026-06-01', 600000,  293200,  'NORMAL',  'Budget LT-892-CE'),
  ('f5000005-0000-4000-8000-000000000005', 'FLEET',   'b0000001-0000-4000-8000-000000000001', 'a0000003-0000-4000-8000-000000000003', '2026-05-01', 2500000, 2520000, 'EXCEEDED','Budget mai — dépassé de 20 000 FCFA'),
  ('f5000006-0000-4000-8000-000000000006', 'FLEET',   'b0000003-0000-4000-8000-000000000003', 'a0000006-0000-4000-8000-000000000006', '2026-06-01', 900000,  145000,  'NORMAL',  'Budget Bafoussam juin')
ON CONFLICT (scope, entity_id, budget_month) DO NOTHING;

-- Dépenses
INSERT INTO fleet.expenses (id, expense_type, amount, description, status, source_type, vehicle_id, vehicle_registration, fleet_id, manager_id, driver_id, driver_full_name) VALUES
  ('f5000002-0000-4000-8000-000000000002', 'FUEL',        42250,  'Plein Total Bastos',               'APPROVED', 'MANUAL', 'c0000001-0000-4000-8000-000000000001', 'LT-892-CE', 'b0000001-0000-4000-8000-000000000001', 'a0000003-0000-4000-8000-000000000003', 'a0000004-0000-4000-8000-000000000004', 'André Mbarga'),
  ('f5000007-0000-4000-8000-000000000007', 'FUEL',        52000,  'Plein Shell Bonanjo',              'APPROVED', 'MANUAL', 'c0000002-0000-4000-8000-000000000002', 'CE-456-AB', 'b0000002-0000-4000-8000-000000000002', 'a0000003-0000-4000-8000-000000000003', 'a0000005-0000-4000-8000-000000000005', 'Sophie Nguema'),
  ('f5000008-0000-4000-8000-000000000008', 'MAINTENANCE',  85000,  'Vidange + filtres SW-123-DL',      'APPROVED', 'AUTO',   'c0000003-0000-4000-8000-000000000003', 'SW-123-DL', 'b0000001-0000-4000-8000-000000000001', 'a0000003-0000-4000-8000-000000000003', NULL, NULL),
  ('f5000009-0000-4000-8000-000000000009', 'INCIDENT',     15000,  'Réparation rayure LT-892-CE',      'PENDING',  'MANUAL', 'c0000001-0000-4000-8000-000000000001', 'LT-892-CE', 'b0000001-0000-4000-8000-000000000001', 'a0000003-0000-4000-8000-000000000003', 'a0000004-0000-4000-8000-000000000004', 'André Mbarga'),
  ('f5000010-0000-4000-8000-000000000010', 'MAINTENANCE', 120000,  'Remplacement pneus CE-456-AB',     'APPROVED', 'AUTO',   'c0000002-0000-4000-8000-000000000002', 'CE-456-AB', 'b0000002-0000-4000-8000-000000000002', 'a0000003-0000-4000-8000-000000000003', NULL, NULL),
  ('f5000011-0000-4000-8000-000000000011', 'FINE',         25000,  'PV excès vitesse SW-123-DL',       'APPROVED', 'MANUAL', 'c0000003-0000-4000-8000-000000000003', 'SW-123-DL', 'b0000001-0000-4000-8000-000000000001', 'a0000003-0000-4000-8000-000000000003', 'a0000004-0000-4000-8000-000000000004', 'André Mbarga'),
  ('f5000012-0000-4000-8000-000000000012', 'FUEL',         29250,  'Plein Oilibya avant mission',      'APPROVED', 'MANUAL', 'c0000003-0000-4000-8000-000000000003', 'SW-123-DL', 'b0000001-0000-4000-8000-000000000001', 'a0000003-0000-4000-8000-000000000003', 'a0000004-0000-4000-8000-000000000004', 'André Mbarga'),
  ('f5000013-0000-4000-8000-000000000013', 'MAINTENANCE', 250000,  'Révision 80 000 km LT-892-CE',     'APPROVED', 'AUTO',   'c0000001-0000-4000-8000-000000000001', 'LT-892-CE', 'b0000001-0000-4000-8000-000000000001', 'a0000003-0000-4000-8000-000000000003', NULL, NULL),
  ('f5000014-0000-4000-8000-000000000014', 'OTHER',        45000,  'Réparation clim DL-001-YA',        'PENDING',  'MANUAL', 'c0000004-0000-4000-8000-000000000004', 'DL-001-YA', 'b0000002-0000-4000-8000-000000000002', 'a0000003-0000-4000-8000-000000000003', NULL, NULL)
ON CONFLICT (id) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════
-- 15. SCORES CONDUCTEURS
-- ═══════════════════════════════════════════════════════════════
INSERT INTO fleet.driver_scores (id, driver_id, fleet_id, manager_id, period_type, period_start, period_end,
  incident_count, total_trips, fuel_per_100km, fleet_avg_fuel_per_100km, doc_compliance_rate,
  abnormal_maintenance_count, completed_assignments, no_show_assignments,
  incident_score, fuel_score, compliance_score, punctuality_score, maintenance_score, final_score, badge) VALUES
  -- André — Juin (mensuel)
  ('f6000001-0000-4000-8000-000000000001', 'a0000004-0000-4000-8000-000000000004', 'b0000001-0000-4000-8000-000000000001',
   'a0000003-0000-4000-8000-000000000003', 'MONTHLY', '2026-06-01', '2026-06-30',
   1, 18, 13.1, 13.0, 75.0, 1, 11, 0,
   80.0, 92.0, 75.0, 100.0, 90.0, 89.0, 'GOOD'),
  -- André — Mai (mensuel)
  ('f6000002-0000-4000-8000-000000000002', 'a0000004-0000-4000-8000-000000000004', 'b0000001-0000-4000-8000-000000000001',
   'a0000003-0000-4000-8000-000000000003', 'MONTHLY', '2026-05-01', '2026-05-31',
   0, 22, 12.8, 13.0, 80.0, 0, 22, 0,
   100.0, 96.0, 80.0, 100.0, 100.0, 95.0, 'EXCELLENCE'),
  -- Sophie — Juin (mensuel)
  ('f6000003-0000-4000-8000-000000000003', 'a0000005-0000-4000-8000-000000000005', 'b0000002-0000-4000-8000-000000000002',
   'a0000003-0000-4000-8000-000000000003', 'MONTHLY', '2026-06-01', '2026-06-30',
   0, 14, 12.5, 13.0, 83.0, 0, 14, 0,
   100.0, 98.0, 83.0, 100.0, 100.0, 97.0, 'EXCELLENCE'),
  -- André — semaine 1-7 Juin
  ('f6000004-0000-4000-8000-000000000004', 'a0000004-0000-4000-8000-000000000004', 'b0000001-0000-4000-8000-000000000001',
   'a0000003-0000-4000-8000-000000000003', 'WEEKLY', '2026-06-01', '2026-06-07',
   1, 5, 13.2, 13.0, 75.0, 1, 4, 0,
   75.0, 90.0, 75.0, 80.0, 85.0, 82.0, 'GOOD')
ON CONFLICT (driver_id, period_type, period_start) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════
-- 16. MAINTENANCE PRÉVENTIVE
-- ═══════════════════════════════════════════════════════════════

-- Plans
INSERT INTO fleet.maintenance_plans (id, maintenance_type, scope, fleet_id, vehicle_id, manager_id, label, description, interval_km, interval_days, pre_alert_km, pre_alert_days, active) VALUES
  ('mp000001-0000-4000-8000-000000000001', 'OIL_CHANGE',          'FLEET',   'b0000001-0000-4000-8000-000000000001', NULL, 'a0000003-0000-4000-8000-000000000003',
   'Vidange tous les 10 000 km', 'Remplacement huile moteur et filtre toutes les 10 000 km ou 6 mois',  10000, 180, 1000, 15, true),
  ('mp000002-0000-4000-8000-000000000002', 'TIRE_ROTATION',       'FLEET',   'b0000001-0000-4000-8000-000000000001', NULL, 'a0000003-0000-4000-8000-000000000003',
   'Rotation pneus 20 000 km',  'Rotation des pneus toutes les 20 000 km', 20000, NULL, 2000, NULL, true),
  ('mp000003-0000-4000-8000-000000000003', 'BRAKE_INSPECTION',    'VEHICLE', 'b0000001-0000-4000-8000-000000000001', 'c0000001-0000-4000-8000-000000000001', 'a0000003-0000-4000-8000-000000000003',
   'Inspection freins LT-892-CE','Vérification plaquettes et disques', 30000, 365, 3000, 30, true),
  ('mp000004-0000-4000-8000-000000000004', 'GENERAL_INSPECTION',  'FLEET',   'b0000002-0000-4000-8000-000000000002', NULL, 'a0000003-0000-4000-8000-000000000003',
   'Révision annuelle Flotte Douala','Révision générale annuelle de tous les véhicules Douala', NULL, 365, NULL, 30, true),
  ('mp000005-0000-4000-8000-000000000005', 'FILTER_CHANGE',       'FLEET',   'b0000003-0000-4000-8000-000000000003', NULL, 'a0000006-0000-4000-8000-000000000006',
   'Remplacement filtres Bafoussam','Filtres air + habitacle toutes les 15 000 km', 15000, NULL, 1500, NULL, true)
ON CONFLICT (id) DO NOTHING;

-- Alertes maintenance préventive
INSERT INTO fleet.maintenance_alerts (id, plan_id, maintenance_type, vehicle_id, vehicle_registration, fleet_id, manager_id,
  status, trigger_type, last_maintenance_km, target_km, current_km, km_remaining, last_maintenance_date, target_date, days_remaining) VALUES
  ('ma000001-0000-4000-8000-000000000001', 'mp000001-0000-4000-8000-000000000001', 'OIL_CHANGE',       'c0000003-0000-4000-8000-000000000003', 'SW-123-DL', 'b0000001-0000-4000-8000-000000000001', 'a0000003-0000-4000-8000-000000000003',
   'OVERDUE', 'BOTH', 44300, 54300, 54300, 0, '2025-11-10', '2026-05-10', -28),
  ('ma000002-0000-4000-8000-000000000002', 'mp000001-0000-4000-8000-000000000001', 'OIL_CHANGE',       'c0000006-0000-4000-8000-000000000006', 'YA-777-LT', 'b0000001-0000-4000-8000-000000000001', 'a0000003-0000-4000-8000-000000000003',
   'DUE',    'MILEAGE', 58000, 68000, 68000, 0, '2025-09-01', NULL, NULL),
  ('ma000003-0000-4000-8000-000000000003', 'mp000002-0000-4000-8000-000000000002', 'TIRE_ROTATION',    'c0000001-0000-4000-8000-000000000001', 'LT-892-CE', 'b0000001-0000-4000-8000-000000000001', 'a0000003-0000-4000-8000-000000000003',
   'UPCOMING','MILEAGE', 70000, 90000, 87420, 2580, '2026-03-15', NULL, NULL),
  ('ma000004-0000-4000-8000-000000000004', 'mp000003-0000-4000-8000-000000000003', 'BRAKE_INSPECTION', 'c0000001-0000-4000-8000-000000000001', 'LT-892-CE', 'b0000001-0000-4000-8000-000000000001', 'a0000003-0000-4000-8000-000000000003',
   'UPCOMING','DATE', NULL, NULL, NULL, NULL, '2025-09-01', '2026-09-01', 85)
ON CONFLICT (vehicle_id, maintenance_type) WHERE status != 'RESOLVED' DO NOTHING;

-- ═══════════════════════════════════════════════════════════════
-- 17. RÈGLES D'ALERTES MÉTIER
-- ═══════════════════════════════════════════════════════════════

-- Règles d'alerte
INSERT INTO fleet.alert_rules (id, name, description, manager_id, trigger_type, action_type, target_role, active, system_template, condition_value) VALUES
  ('ar000001-0000-4000-8000-000000000001', 'Alerte doc expiration J-30',    'Notifier manager 30 jours avant expiration document',    'a0000003-0000-4000-8000-000000000003', 'DOCUMENT_EXPIRY',       'IN_APP_NOTIFICATION', 'MANAGER', true, true, '30'),
  ('ar000002-0000-4000-8000-000000000002', 'Alerte budget 80%',              'Notifier quand budget consommé à 80%',                   'a0000003-0000-4000-8000-000000000003', 'BUDGET_THRESHOLD',      'IN_APP_NOTIFICATION', 'MANAGER', true, true, '80'),
  ('ar000003-0000-4000-8000-000000000003', 'Alerte budget dépassé',          'Notifier quand budget dépassé (100%)',                   'a0000003-0000-4000-8000-000000000003', 'BUDGET_THRESHOLD',      'EMAIL',               'MANAGER', true, true, '100'),
  ('ar000004-0000-4000-8000-000000000004', 'Alerte maintenance échue',       'Notifier quand entretien préventif est dû',              'a0000003-0000-4000-8000-000000000003', 'MAINTENANCE_ALERT_DUE', 'IN_APP_NOTIFICATION', 'MANAGER', true, true, NULL),
  ('ar000005-0000-4000-8000-000000000005', 'Alerte incident signalé',        'Notifier le manager à chaque incident déclaré',         'a0000003-0000-4000-8000-000000000003', 'INCIDENT_REPORTED',     'IN_APP_NOTIFICATION', 'MANAGER', true, true, NULL),
  ('ar000006-0000-4000-8000-000000000006', 'Score conducteur dégradé',       'Notifier si score mensuel < 70',                        'a0000003-0000-4000-8000-000000000003', 'DRIVER_SCORE_DROP',     'IN_APP_NOTIFICATION', 'MANAGER', true, false, '70'),
  ('ar000007-0000-4000-8000-000000000007', 'Anomalie carburant',             'Détection consommation anormale > 20% au-dessus moyenne','a0000003-0000-4000-8000-000000000003', 'FUEL_ANOMALY',          'IN_APP_NOTIFICATION', 'MANAGER', true, false, '20'),
  ('ar000008-0000-4000-8000-000000000008', 'Alerte doc expiration J-7',      'Alerte urgente 7 jours avant expiration',               'a0000006-0000-4000-8000-000000000006', 'DOCUMENT_EXPIRY',       'EMAIL',               'MANAGER', true, true, '7'),
  ('ar000009-0000-4000-8000-000000000009', 'Notification chauffeur — aff.',  'Notifier chauffeur de sa prochaine affectation',        'a0000003-0000-4000-8000-000000000003', 'INCIDENT_REPORTED',     'IN_APP_NOTIFICATION', 'DRIVER',  true, false, NULL)
ON CONFLICT (id) DO NOTHING;

-- Événements d'alerte déclenchés
INSERT INTO fleet.alert_events (id, rule_id, rule_name, manager_id, trigger_type, action_type, title, message, source_entity_id, source_entity_type, read_status) VALUES
  ('ae000001-0000-4000-8000-000000000001', 'ar000001-0000-4000-8000-000000000001', 'Alerte doc expiration J-30',
   'a0000003-0000-4000-8000-000000000003', 'DOCUMENT_EXPIRY', 'IN_APP_NOTIFICATION',
   'Document bientôt expiré — CE-456-AB',
   'Le contrôle technique du véhicule CE-456-AB expire le 30/06/2026 (dans 22 jours). Pensez à le renouveler.',
   'f2000002-0000-4000-8000-000000000002', 'VEHICLE_DOCUMENT', 'UNREAD'),
  ('ae000002-0000-4000-8000-000000000002', 'ar000003-0000-4000-8000-000000000003', 'Alerte budget dépassé',
   'a0000003-0000-4000-8000-000000000003', 'BUDGET_THRESHOLD', 'EMAIL',
   'Budget Flotte Douala dépassé à 85%',
   'Le budget mensuel de la Flotte Douala est consommé à 85% (1 530 000 / 1 800 000 FCFA). Attention au dépassement.',
   'f5000003-0000-4000-8000-000000000003', 'BUDGET', 'UNREAD'),
  ('ae000003-0000-4000-8000-000000000003', 'ar000004-0000-4000-8000-000000000004', 'Alerte maintenance échue',
   'a0000003-0000-4000-8000-000000000003', 'MAINTENANCE_ALERT_DUE', 'IN_APP_NOTIFICATION',
   'Maintenance en retard — SW-123-DL',
   'La vidange du véhicule SW-123-DL est en retard de 28 jours. Planifiez l''entretien immédiatement.',
   'ma000001-0000-4000-8000-000000000001', 'MAINTENANCE_ALERT', 'UNREAD'),
  ('ae000004-0000-4000-8000-000000000004', 'ar000005-0000-4000-8000-000000000005', 'Alerte incident signalé',
   'a0000003-0000-4000-8000-000000000003', 'INCIDENT_REPORTED', 'IN_APP_NOTIFICATION',
   'Incident signalé — LT-892-CE',
   'André Mbarga a signalé un accident sur le véhicule LT-892-CE (rayure parking). Statut : EN COURS D''INVESTIGATION.',
   'f1000009-0000-4000-8000-000000000009', 'INCIDENT', 'READ'),
  ('ae000005-0000-4000-8000-000000000005', 'ar000001-0000-4000-8000-000000000001', 'Alerte doc expiration J-30',
   'a0000003-0000-4000-8000-000000000003', 'DOCUMENT_EXPIRY', 'IN_APP_NOTIFICATION',
   'Permis de transport expiré — YA-777-LT',
   'Le permis de transport du véhicule YA-777-LT est expiré depuis le 10/05/2026. Renouvelez immédiatement.',
   'f2000008-0000-4000-8000-000000000008', 'VEHICLE_DOCUMENT', 'UNREAD')
ON CONFLICT (id) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════
-- 18. NOTIFICATIONS & PARAMÈTRES
-- ═══════════════════════════════════════════════════════════════

-- Paramètres notification
INSERT INTO fleet.notification_settings (user_id, enable_email, enable_push, enable_sms, enable_whatsapp) VALUES
  ('a0000001-0000-4000-8000-000000000001', true, true,  false, false),
  ('a0000002-0000-4000-8000-000000000002', true, true,  false, false),
  ('a0000003-0000-4000-8000-000000000003', true, true,  true,  false),
  ('a0000004-0000-4000-8000-000000000004', true, true,  false, false),
  ('a0000005-0000-4000-8000-000000000005', true, true,  false, false),
  ('a0000006-0000-4000-8000-000000000006', true, true,  true,  false)
ON CONFLICT (user_id) DO NOTHING;

-- Notifications
INSERT INTO fleet.notifications (id, user_id, title, message, type, is_read) VALUES
  ('f7000001-0000-4000-8000-000000000001', 'a0000003-0000-4000-8000-000000000003',
   'Incident signalé — LT-892-CE',
   'André Mbarga a signalé une rayure latérale sur le véhicule LT-892-CE au parking Carrefour Bastos.', 'WARNING', false),
  ('f7000002-0000-4000-8000-000000000002', 'a0000004-0000-4000-8000-000000000004',
   'Affectation confirmée',
   'Votre affectation du 10 Juin 2026, 07h00-12h00, véhicule LT-892-CE, trajet Yaoundé→Mbalmayo est confirmée.', 'INFO', false),
  ('f7000003-0000-4000-8000-000000000003', 'a0000003-0000-4000-8000-000000000003',
   'Document expiré — YA-777-LT',
   'Le permis de transport du véhicule YA-777-LT est expiré depuis le 10/05/2026. Veuillez le renouveler.', 'WARNING', false),
  ('f7000004-0000-4000-8000-000000000004', 'a0000003-0000-4000-8000-000000000003',
   'Budget Flotte Douala à 85%',
   'Le budget mensuel de la Flotte Douala est consommé à 85%. Surveiller les dépenses restantes.', 'WARNING', false),
  ('f7000005-0000-4000-8000-000000000005', 'a0000004-0000-4000-8000-000000000004',
   'Trajet en cours',
   'Votre trajet LT-892-CE est en cours depuis 10h30. Bonne route !', 'INFO', true),
  ('f7000006-0000-4000-8000-000000000006', 'a0000005-0000-4000-8000-000000000005',
   'Nouveau planning publié',
   'Le planning de la semaine 10-16 Juin 2026 pour la Flotte Douala a été publié.', 'INFO', false),
  ('f7000007-0000-4000-8000-000000000007', 'a0000003-0000-4000-8000-000000000003',
   'Maintenance en retard — SW-123-DL',
   'La vidange du SW-123-DL est en retard de 28 jours. Planifiez l''entretien.', 'WARNING', false),
  ('f7000008-0000-4000-8000-000000000008', 'a0000003-0000-4000-8000-000000000003',
   'Incident critique — YA-777-LT',
   'Une collision a été signalée sur le véhicule YA-777-LT. Coût estimé : 180 000 FCFA.', 'WARNING', false),
  ('f7000009-0000-4000-8000-000000000009', 'a0000004-0000-4000-8000-000000000004',
   'Score mensuel disponible',
   'Votre score conducteur de mai 2026 est disponible : 95/100 — Badge EXCELLENCE.', 'INFO', true),
  ('f7000010-0000-4000-8000-000000000010', 'a0000001-0000-4000-8000-000000000001',
   'Nouveau gestionnaire enregistré',
   'Claire Fouda (Logistics Douala SARL) vient de rejoindre la plateforme.', 'INFO', false)
ON CONFLICT (id) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════
-- VÉRIFICATION FINALE
-- ═══════════════════════════════════════════════════════════════
SELECT
  'users'               AS table_name, count(*) FROM fleet.users            UNION ALL
  SELECT 'fleet_managers',              count(*) FROM fleet.fleet_managers   UNION ALL
  SELECT 'fleets',                      count(*) FROM fleet.fleets           UNION ALL
  SELECT 'vehicles',                    count(*) FROM fleet.vehicles         UNION ALL
  SELECT 'drivers',                     count(*) FROM fleet.drivers          UNION ALL
  SELECT 'trips',                       count(*) FROM fleet.trips            UNION ALL
  SELECT 'maintenances',                count(*) FROM fleet.maintenances     UNION ALL
  SELECT 'incidents',                   count(*) FROM fleet.incidents        UNION ALL
  SELECT 'fuel_recharges',              count(*) FROM fleet.fuel_recharges   UNION ALL
  SELECT 'vehicle_documents',           count(*) FROM fleet.vehicle_documents UNION ALL
  SELECT 'driver_documents',            count(*) FROM fleet.driver_documents UNION ALL
  SELECT 'schedules',                   count(*) FROM fleet.schedules        UNION ALL
  SELECT 'assignments',                 count(*) FROM fleet.assignments      UNION ALL
  SELECT 'kpi_snapshots',               count(*) FROM fleet.kpi_snapshots   UNION ALL
  SELECT 'budgets',                     count(*) FROM fleet.budgets          UNION ALL
  SELECT 'expenses',                    count(*) FROM fleet.expenses         UNION ALL
  SELECT 'driver_scores',               count(*) FROM fleet.driver_scores    UNION ALL
  SELECT 'maintenance_plans',           count(*) FROM fleet.maintenance_plans UNION ALL
  SELECT 'maintenance_alerts',          count(*) FROM fleet.maintenance_alerts UNION ALL
  SELECT 'alert_rules',                 count(*) FROM fleet.alert_rules      UNION ALL
  SELECT 'alert_events',                count(*) FROM fleet.alert_events     UNION ALL
  SELECT 'notifications',               count(*) FROM fleet.notifications    UNION ALL
  SELECT 'geofence_zones',              count(*) FROM fleet.geofence_zones   UNION ALL
  SELECT 'geofence_events',             count(*) FROM fleet.geofence_events
ORDER BY table_name;
