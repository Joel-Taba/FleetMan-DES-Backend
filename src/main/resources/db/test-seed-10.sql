-- Seed de test FleetMan — max 10 enregistrements par entité métier
-- Idempotent (ON CONFLICT DO NOTHING). Compatible fake-auth / manager1.

-- Comptes chauffeurs additionnels (login fake-auth via comptes dynamiques après création UI ;
-- ici : données locales pour listes/tests Manager)
INSERT INTO fleet.users (id, username, email, first_name, last_name, is_active, kernel_id)
VALUES
  ('a1000001-0000-4000-8000-000000000001', 'driver.kouam',  'driver2@fleetman.cm', 'Paul',   'Kouam',  true, 'a1000001-0000-4000-8000-000000000001'),
  ('a1000002-0000-4000-8000-000000000002', 'driver.nana',   'driver3@fleetman.cm', 'Amina',  'Nana',   true, 'a1000002-0000-4000-8000-000000000002'),
  ('a1000003-0000-4000-8000-000000000003', 'driver.fouda',  'driver4@fleetman.cm', 'Eric',   'Fouda',  true, 'a1000003-0000-4000-8000-000000000003'),
  ('a1000004-0000-4000-8000-000000000004', 'driver.bella',  'driver5@fleetman.cm', 'Claire', 'Bella',  true, 'a1000004-0000-4000-8000-000000000004'),
  ('a1000005-0000-4000-8000-000000000005', 'driver.mvogo',  'driver6@fleetman.cm', 'Serge',  'Mvogo',  true, 'a1000005-0000-4000-8000-000000000005'),
  ('a1000007-0000-4000-8000-000000000007', 'driver.biya',   'driver8@fleetman.cm', 'Joel',   'Biya',   true, 'a1000007-0000-4000-8000-000000000007'),
  ('a1000008-0000-4000-8000-000000000008', 'driver.ongolo', 'driver9@fleetman.cm', 'Linda',  'Ongolo', true, 'a1000008-0000-4000-8000-000000000008'),
  ('a1000009-0000-4000-8000-000000000009', 'driver.meke',   'driver10@fleetman.cm','Bruno',  'Meke',   true, 'a1000009-0000-4000-8000-000000000009')
ON CONFLICT (id) DO NOTHING;

-- Relâche éventuelle assignation unique avant inserts drivers
UPDATE fleet.vehicles SET current_driver_id = NULL
WHERE id IN (
  'c0000001-0000-4000-8000-000000000001',
  'c0000002-0000-4000-8000-000000000002',
  'c0000003-0000-4000-8000-000000000003'
);

INSERT INTO fleet.drivers (user_id, fleet_id, licence_number, status, assigned_vehicle_id)
VALUES
  ('a1000001-0000-4000-8000-000000000001', 'b0000001-0000-4000-8000-000000000001', 'B2000001CM', 'ACTIVE', NULL),
  ('a1000002-0000-4000-8000-000000000002', 'b0000001-0000-4000-8000-000000000001', 'B2000002CM', 'ACTIVE', NULL),
  ('a1000003-0000-4000-8000-000000000003', 'b0000001-0000-4000-8000-000000000001', 'B2000003CM', 'ACTIVE', NULL),
  ('a1000004-0000-4000-8000-000000000004', 'b0000002-0000-4000-8000-000000000002', 'B2000004CM', 'ACTIVE', NULL),
  ('a1000005-0000-4000-8000-000000000005', 'b0000002-0000-4000-8000-000000000002', 'B2000005CM', 'ACTIVE', NULL),
  ('a1000007-0000-4000-8000-000000000007', 'b0000001-0000-4000-8000-000000000001', 'B2000007CM', 'ACTIVE', NULL),
  ('a1000008-0000-4000-8000-000000000008', 'b0000001-0000-4000-8000-000000000001', 'B2000008CM', 'ACTIVE', NULL),
  ('a1000009-0000-4000-8000-000000000009', 'b0000002-0000-4000-8000-000000000002', 'B2000009CM', 'ACTIVE', NULL)
ON CONFLICT (user_id) DO NOTHING;

-- Remet le chauffeur demo principal
UPDATE fleet.drivers
SET assigned_vehicle_id = 'c0000001-0000-4000-8000-000000000001', status = 'ACTIVE'
WHERE user_id = '35944e04-43c1-4eba-8acf-13f72a3ca5be';

UPDATE fleet.vehicles
SET current_driver_id = '35944e04-43c1-4eba-8acf-13f72a3ca5be'
WHERE id = 'c0000001-0000-4000-8000-000000000001';

-- Véhicules (cible totale <= 10 avec les 3 existants)
INSERT INTO fleet.vehicles (id, fleet_id, manager_id, license_plate, brand, model, manufacturing_year, color, status, vehicle_type_id)
SELECT 'c0000004-0000-4000-8000-000000000004', 'b0000001-0000-4000-8000-000000000001', 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb',
       'YT-101-AA', 'Toyota', 'Hiace', 2023, 'Blanc', 'AVAILABLE', vt.id
FROM fleet.vehicle_types vt WHERE vt.code = 'VAN' OR vt.code = 'CAR' LIMIT 1
ON CONFLICT (id) DO NOTHING;

INSERT INTO fleet.vehicles (id, fleet_id, manager_id, license_plate, brand, model, manufacturing_year, color, status, vehicle_type_id)
SELECT 'c0000005-0000-4000-8000-000000000005', 'b0000001-0000-4000-8000-000000000001', 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb',
       'YT-102-BB', 'Hyundai', 'H1', 2022, 'Noir', 'AVAILABLE', vt.id
FROM fleet.vehicle_types vt WHERE vt.code IN ('VAN','CAR','TRUCK') LIMIT 1
ON CONFLICT (id) DO NOTHING;

INSERT INTO fleet.vehicles (id, fleet_id, manager_id, license_plate, brand, model, manufacturing_year, color, status, vehicle_type_id)
SELECT 'c0000006-0000-4000-8000-000000000006', 'b0000002-0000-4000-8000-000000000002', 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb',
       'DL-201-CC', 'Isuzu', 'NPR', 2021, 'Rouge', 'AVAILABLE', vt.id
FROM fleet.vehicle_types vt WHERE vt.code = 'TRUCK' LIMIT 1
ON CONFLICT (id) DO NOTHING;

INSERT INTO fleet.vehicles (id, fleet_id, manager_id, license_plate, brand, model, manufacturing_year, color, status, vehicle_type_id)
SELECT 'c0000007-0000-4000-8000-000000000007', 'b0000002-0000-4000-8000-000000000002', 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb',
       'DL-202-DD', 'Renault', 'Master', 2020, 'Gris', 'MAINTENANCE', vt.id
FROM fleet.vehicle_types vt WHERE vt.code IN ('VAN','CAR') LIMIT 1
ON CONFLICT (id) DO NOTHING;

INSERT INTO fleet.vehicles (id, fleet_id, manager_id, license_plate, brand, model, manufacturing_year, color, status, vehicle_type_id)
SELECT 'c0000008-0000-4000-8000-000000000008', 'b0000001-0000-4000-8000-000000000001', 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb',
       'YT-103-EE', 'Peugeot', '301', 2024, 'Bleu', 'AVAILABLE', vt.id
FROM fleet.vehicle_types vt WHERE vt.code = 'CAR' LIMIT 1
ON CONFLICT (id) DO NOTHING;

INSERT INTO fleet.vehicles (id, fleet_id, manager_id, license_plate, brand, model, manufacturing_year, color, status, vehicle_type_id)
SELECT 'c0000009-0000-4000-8000-000000000009', 'b0000002-0000-4000-8000-000000000002', 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb',
       'DL-203-FF', 'Mercedes', 'Sprinter', 2019, 'Blanc', 'AVAILABLE', vt.id
FROM fleet.vehicle_types vt WHERE vt.code IN ('VAN','CAR','TRUCK') LIMIT 1
ON CONFLICT (id) DO NOTHING;

INSERT INTO fleet.vehicles (id, fleet_id, manager_id, license_plate, brand, model, manufacturing_year, color, status, vehicle_type_id)
SELECT 'c0000010-0000-4000-8000-000000000010', 'b0000001-0000-4000-8000-000000000001', 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb',
       'YT-104-GG', 'Ford', 'Transit', 2023, 'Vert', 'ON_TRIP', vt.id
FROM fleet.vehicle_types vt WHERE vt.code IN ('VAN','CAR') LIMIT 1
ON CONFLICT (id) DO NOTHING;

-- Paramètres opérationnels pour nouveaux véhicules
INSERT INTO fleet.operational_parameters (id, vehicle_id, mileage, fuel_level, status)
VALUES
  ('ca000004-0000-4000-8000-000000000004', 'c0000004-0000-4000-8000-000000000004', 12000, '70', true),
  ('ca000005-0000-4000-8000-000000000005', 'c0000005-0000-4000-8000-000000000005', 24500, '55', true),
  ('ca000006-0000-4000-8000-000000000006', 'c0000006-0000-4000-8000-000000000006', 88000, '40', true),
  ('ca000007-0000-4000-8000-000000000007', 'c0000007-0000-4000-8000-000000000007', 102000, '20', true),
  ('ca000008-0000-4000-8000-000000000008', 'c0000008-0000-4000-8000-000000000008', 5600, '90', true),
  ('ca000009-0000-4000-8000-000000000009', 'c0000009-0000-4000-8000-000000000009', 67000, '35', true),
  ('ca000010-0000-4000-8000-000000000010', 'c0000010-0000-4000-8000-000000000010', 18300, '48', true)
ON CONFLICT (id) DO NOTHING;

-- Trajets (total cible <= 10)
INSERT INTO fleet.trips (id, trip_code, vehicle_id, driver_id, fleet_id, created_by, start_date, end_date, start_time, end_time, status, distance_km, duration_minutes, mission_object)
VALUES
  ('d0000004-0000-4000-8000-000000000004', 'TRJ-2026-0004', 'c0000004-0000-4000-8000-000000000004', 'a1000001-0000-4000-8000-000000000001',
   'b0000001-0000-4000-8000-000000000001', 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb',
   CURRENT_DATE - 2, CURRENT_DATE - 2, '07:00', '11:30', 'COMPLETED', 96, 270, 'Livraison Bastos'),
  ('d0000005-0000-4000-8000-000000000005', 'TRJ-2026-0005', 'c0000005-0000-4000-8000-000000000005', 'a1000002-0000-4000-8000-000000000002',
   'b0000001-0000-4000-8000-000000000001', 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb',
   CURRENT_DATE - 1, CURRENT_DATE - 1, '09:00', '15:00', 'COMPLETED', 142, 360, 'Mission VIP'),
  ('d0000006-0000-4000-8000-000000000006', 'TRJ-2026-0006', 'c0000006-0000-4000-8000-000000000006', 'a1000004-0000-4000-8000-000000000004',
   'b0000002-0000-4000-8000-000000000002', 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb',
   CURRENT_DATE, NULL, '08:00', NULL, 'DEPARTED', NULL, NULL, 'Navette Douala Port'),
  ('d0000007-0000-4000-8000-000000000007', 'TRJ-2026-0007', 'c0000008-0000-4000-8000-000000000008', 'a1000007-0000-4000-8000-000000000007',
   'b0000001-0000-4000-8000-000000000001', 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb',
   CURRENT_DATE + 1, NULL, '06:30', NULL, 'SCHEDULED', NULL, NULL, 'Transfert aéroport'),
  ('d0000008-0000-4000-8000-000000000008', 'TRJ-2026-0008', 'c0000002-0000-4000-8000-000000000002', 'a1000005-0000-4000-8000-000000000005',
   'b0000002-0000-4000-8000-000000000002', 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb',
   CURRENT_DATE - 5, CURRENT_DATE - 5, '10:00', '12:00', 'CANCELLED', NULL, NULL, 'Annulé client'),
  ('d0000009-0000-4000-8000-000000000009', 'TRJ-2026-0009', 'c0000009-0000-4000-8000-000000000009', 'a1000009-0000-4000-8000-000000000009',
   'b0000002-0000-4000-8000-000000000002', 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb',
   CURRENT_DATE - 3, CURRENT_DATE - 3, '13:00', '17:45', 'COMPLETED', 78, 285, 'Livraison Bonanjo'),
  ('d0000010-0000-4000-8000-000000000010', 'TRJ-2026-0010', 'c0000010-0000-4000-8000-000000000010', 'a1000003-0000-4000-8000-000000000003',
   'b0000001-0000-4000-8000-000000000001', 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb',
   CURRENT_DATE, NULL, '11:00', NULL, 'DEPARTED', NULL, NULL, 'Course express')
ON CONFLICT (id) DO NOTHING;

-- Schedules / assignments (<= 10)
INSERT INTO fleet.schedules (id, fleet_id, manager_id, title, period_type, start_date, end_date, status)
VALUES
  ('f3000003-0000-4000-8000-000000000005', 'b0000001-0000-4000-8000-000000000001', 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb',
   'Planning semaine courante', 'WEEKLY', date_trunc('week', CURRENT_DATE)::date, (date_trunc('week', CURRENT_DATE) + INTERVAL '6 day')::date, 'PUBLISHED'),
  ('f3000003-0000-4000-8000-000000000006', 'b0000002-0000-4000-8000-000000000002', 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb',
   'Planning Douala août', 'MONTHLY', '2026-08-01', '2026-08-31', 'DRAFT')
ON CONFLICT (id) DO NOTHING;

INSERT INTO fleet.assignments (id, schedule_id, fleet_id, vehicle_id, driver_id, start_datetime, end_datetime, status, estimated_km, start_location, end_location)
VALUES
  ('f3000005-0000-4000-8000-000000000005', 'f3000003-0000-4000-8000-000000000005', 'b0000001-0000-4000-8000-000000000001',
   'c0000004-0000-4000-8000-000000000004', 'a1000001-0000-4000-8000-000000000001',
   (CURRENT_DATE + INTERVAL '1 day') + TIME '08:00', (CURRENT_DATE + INTERVAL '1 day') + TIME '12:00', 'PENDING', 90, 'Yaoundé centre', 'Nsimalen'),
  ('f3000005-0000-4000-8000-000000000006', 'f3000003-0000-4000-8000-000000000005', 'b0000001-0000-4000-8000-000000000001',
   'c0000005-0000-4000-8000-000000000005', 'a1000002-0000-4000-8000-000000000002',
   (CURRENT_DATE + INTERVAL '2 day') + TIME '09:00', (CURRENT_DATE + INTERVAL '2 day') + TIME '17:00', 'PENDING', 150, 'Mvog-Ada', 'Mballa II'),
  ('f3000005-0000-4000-8000-000000000007', 'f3000001-0000-4000-8000-000000000001', 'b0000001-0000-4000-8000-000000000001',
   'c0000008-0000-4000-8000-000000000008', 'a1000007-0000-4000-8000-000000000007',
   '2026-06-06 08:00:00', '2026-06-06 11:00:00', 'COMPLETED', 45, 'Bastos', 'Omnisports'),
  ('f3000005-0000-4000-8000-000000000008', 'f3000003-0000-4000-8000-000000000006', 'b0000002-0000-4000-8000-000000000002',
   'c0000006-0000-4000-8000-000000000006', 'a1000004-0000-4000-8000-000000000004',
   '2026-08-05 07:00:00', '2026-08-05 15:00:00', 'PENDING', 200, 'Akwa', 'Bonabéri'),
  ('f3000005-0000-4000-8000-000000000009', 'f3000003-0000-4000-8000-000000000006', 'b0000002-0000-4000-8000-000000000002',
   'c0000009-0000-4000-8000-000000000009', 'a1000005-0000-4000-8000-000000000005',
   '2026-08-10 10:00:00', '2026-08-10 14:00:00', 'PENDING', 70, 'Deido', 'Yassa'),
  ('f3000005-0000-4000-8000-000000000010', 'f3000003-0000-4000-8000-000000000005', 'b0000001-0000-4000-8000-000000000001',
   'c0000010-0000-4000-8000-000000000010', 'a1000003-0000-4000-8000-000000000003',
   (CURRENT_DATE) + TIME '14:00', (CURRENT_DATE) + TIME '18:00', 'IN_PROGRESS', 55, 'Essos', 'Nkolbisson')
ON CONFLICT (id) DO NOTHING;

-- Ops
INSERT INTO fleet.incidents (id, type, description, severity, vehicle_id, vehicle_registration, driver_id, driver_full_name, status)
VALUES
  ('f1000011-0000-4000-8000-000000000011', 'BREAKDOWN', 'Panne batterie', 'HIGH', 'c0000007-0000-4000-8000-000000000007', 'DL-202-DD', 'a1000005-0000-4000-8000-000000000005', 'Serge Mvogo', 'UNDER_INVESTIGATION'),
  ('f1000012-0000-4000-8000-000000000012', 'TRAFFIC_VIOLATION', 'Excès de vitesse signalé', 'LOW', 'c0000004-0000-4000-8000-000000000004', 'YT-101-AA', 'a1000001-0000-4000-8000-000000000001', 'Paul Kouam', 'REPORTED'),
  ('f1000013-0000-4000-8000-000000000013', 'OTHER', 'Pneu crevé', 'MEDIUM', 'c0000005-0000-4000-8000-000000000005', 'YT-102-BB', 'a1000002-0000-4000-8000-000000000002', 'Amina Nana', 'RESOLVED')
ON CONFLICT (id) DO NOTHING;

INSERT INTO fleet.maintenances (id, subject, cost, vehicle_id, vehicle_registration, driver_id, driver_full_name)
VALUES
  ('f1000014-0000-4000-8000-000000000014', 'Remplacement pneus', 180000, 'c0000002-0000-4000-8000-000000000002', 'CE-456-AB', 'a1000004-0000-4000-8000-000000000004', 'Claire Bella'),
  ('f1000015-0000-4000-8000-000000000015', 'Contrôle freins', 65000, 'c0000007-0000-4000-8000-000000000007', 'DL-202-DD', NULL, NULL),
  ('f1000016-0000-4000-8000-000000000016', 'Vidange moteur', 45000, 'c0000006-0000-4000-8000-000000000006', 'DL-201-CC', 'a1000005-0000-4000-8000-000000000005', 'Serge Mvogo')
ON CONFLICT (id) DO NOTHING;

INSERT INTO fleet.fuel_recharges (id, quantity, price, station_name, vehicle_id, vehicle_registration, driver_id, driver_full_name)
VALUES
  ('f1000017-0000-4000-8000-000000000017', 48, 31200, 'SHELL', 'c0000004-0000-4000-8000-000000000004', 'YT-101-AA', 'a1000001-0000-4000-8000-000000000001', 'Paul Kouam'),
  ('f1000018-0000-4000-8000-000000000018', 70, 45500, 'TOTAL', 'c0000006-0000-4000-8000-000000000006', 'DL-201-CC', 'a1000004-0000-4000-8000-000000000004', 'Claire Bella'),
  ('f1000019-0000-4000-8000-000000000019', 35, 22750, 'OILIBYA', 'c0000008-0000-4000-8000-000000000008', 'YT-103-EE', 'a1000007-0000-4000-8000-000000000007', 'Joel Biya')
ON CONFLICT (id) DO NOTHING;

-- Documents
INSERT INTO fleet.vehicle_documents (id, vehicle_id, doc_type, doc_number, expiry_date, status)
VALUES
  ('f2000011-0000-4000-8000-000000000011', 'c0000004-0000-4000-8000-000000000004', 'INSURANCE', 'ASS-2026-1001', CURRENT_DATE + 180, 'VALID'),
  ('f2000012-0000-4000-8000-000000000012', 'c0000005-0000-4000-8000-000000000005', 'REGISTRATION', 'CG-2026-88', CURRENT_DATE + 20, 'EXPIRING_SOON'),
  ('f2000013-0000-4000-8000-000000000013', 'c0000006-0000-4000-8000-000000000006', 'TECHNICAL_CONTROL', 'VT-2025-999', CURRENT_DATE - 10, 'EXPIRED'),
  ('f2000014-0000-4000-8000-000000000014', 'c0000008-0000-4000-8000-000000000008', 'TAX_STICKER', 'TAX-2026-14', CURRENT_DATE + 90, 'VALID')
ON CONFLICT (id) DO NOTHING;

INSERT INTO fleet.driver_documents (id, driver_id, doc_type, doc_number, expiry_date, status)
VALUES
  ('f2000015-0000-4000-8000-000000000015', 'a1000001-0000-4000-8000-000000000001', 'DRIVING_LICENSE', 'B2000001CM', CURRENT_DATE + 400, 'VALID'),
  ('f2000016-0000-4000-8000-000000000016', 'a1000002-0000-4000-8000-000000000002', 'MEDICAL_CERT', 'MED-22', CURRENT_DATE + 15, 'EXPIRING_SOON'),
  ('f2000017-0000-4000-8000-000000000017', 'a1000004-0000-4000-8000-000000000004', 'DRIVING_LICENSE', 'B2000004CM', CURRENT_DATE + 200, 'VALID')
ON CONFLICT (id) DO NOTHING;

-- Budgets / dépenses
INSERT INTO fleet.budgets (id, scope, entity_id, manager_id, budget_month, amount, consumed, alert_level)
VALUES
  ('f5000011-0000-4000-8000-000000000011', 'FLEET', 'b0000002-0000-4000-8000-000000000002',
   'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb', date_trunc('month', CURRENT_DATE)::date, 1800000, 255000, 'NORMAL'),
  ('f5000012-0000-4000-8000-000000000012', 'VEHICLE', 'c0000001-0000-4000-8000-000000000001',
   'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb', date_trunc('month', CURRENT_DATE)::date, 250000, 90000, 'WARNING')
ON CONFLICT (id) DO NOTHING;

INSERT INTO fleet.expenses (id, expense_type, amount, description, status, source_type, vehicle_id, vehicle_registration, fleet_id, manager_id, driver_id, driver_full_name)
VALUES
  ('f5000013-0000-4000-8000-000000000013', 'FUEL', 31200, 'Plein Shell', 'APPROVED', 'MANUAL',
   'c0000004-0000-4000-8000-000000000004', 'YT-101-AA', 'b0000001-0000-4000-8000-000000000001',
   'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb', 'a1000001-0000-4000-8000-000000000001', 'Paul Kouam'),
  ('f5000014-0000-4000-8000-000000000014', 'MAINTENANCE', 65000, 'Freins', 'PENDING', 'MANUAL',
   'c0000007-0000-4000-8000-000000000007', 'DL-202-DD', 'b0000002-0000-4000-8000-000000000002',
   'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb', NULL, NULL),
  ('f5000015-0000-4000-8000-000000000015', 'TOLL', 2500, 'Péage Edéa', 'APPROVED', 'MANUAL',
   'c0000006-0000-4000-8000-000000000006', 'DL-201-CC', 'b0000002-0000-4000-8000-000000000002',
   'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb', 'a1000004-0000-4000-8000-000000000004', 'Claire Bella'),
  ('f5000016-0000-4000-8000-000000000016', 'FINE', 15000, 'Amende stationnement', 'REJECTED', 'MANUAL',
   'c0000005-0000-4000-8000-000000000005', 'YT-102-BB', 'b0000001-0000-4000-8000-000000000001',
   'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb', 'a1000002-0000-4000-8000-000000000002', 'Amina Nana')
ON CONFLICT (id) DO NOTHING;

-- Alertes in-app
INSERT INTO fleet.alert_events (id, manager_id, trigger_type, action_type, title, message, source_entity_id, source_entity_type, read_status)
VALUES
  ('f8000001-0000-4000-8000-000000000001', 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb', 'DOCUMENT_EXPIRY', 'IN_APP',
   'Contrôle technique expiré', 'VT du véhicule DL-201-CC expiré', 'c0000006-0000-4000-8000-000000000006', 'VEHICLE', 'UNREAD'),
  ('f8000002-0000-4000-8000-000000000002', 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb', 'MAINTENANCE_DUE', 'IN_APP',
   'Maintenance due', 'Véhicule DL-202-DD en atelier', 'c0000007-0000-4000-8000-000000000007', 'VEHICLE', 'UNREAD'),
  ('f8000003-0000-4000-8000-000000000003', 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb', 'BUDGET_ALERT', 'IN_APP',
   'Budget véhicule', 'Seuil warning atteint sur LT-892-CE', 'c0000001-0000-4000-8000-000000000001', 'VEHICLE', 'READ'),
  ('f8000004-0000-4000-8000-000000000004', 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb', 'INCIDENT', 'IN_APP',
   'Nouvel incident', 'Panne batterie signalée', 'f1000011-0000-4000-8000-000000000011', 'INCIDENT', 'UNREAD')
ON CONFLICT (id) DO NOTHING;

-- Assouplir le plan Starter pour les tests (jusqu'à 10)
UPDATE fleet.subscription_plans
SET max_fleets = GREATEST(max_fleets, 5),
    max_vehicles = GREATEST(max_vehicles, 10),
    max_drivers = GREATEST(max_drivers, 10)
WHERE id = '55000001-0000-4000-8000-000000000001'
   OR name ILIKE 'Starter';

UPDATE fleet.fleet_managers
SET plan_id = COALESCE(plan_id, '55000001-0000-4000-8000-000000000001'),
    subscription_status = 'ACTIVE',
    subscription_start = COALESCE(subscription_start, CURRENT_DATE),
    subscription_end = COALESCE(subscription_end, CURRENT_DATE + INTERVAL '1 year')
WHERE user_id = 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb';
