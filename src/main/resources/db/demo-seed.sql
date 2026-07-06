-- Données de démonstration FleetMan (profil demo)
-- UUID utilisateurs alignés sur le Kernel RT-Comops — voir KERNEL_SETUP_FLEETMAN.md et DemoTestAccounts.java
-- Owner Kernel : joeltaba4@gmail.com | Manager Kernel : manager1@fleetman.cm
-- Chauffeur demo : local uniquement (pas encore provisionné dans le Kernel)

-- 1. Utilisateurs locaux (kernel_id = id pour les comptes Kernel)
INSERT INTO fleet.users (id, username, email, first_name, last_name, is_active, kernel_id)
VALUES
  ('2c9a43d2-8406-4860-b33b-f7ba989885ba', 'joeltaba4', 'joeltaba4@gmail.com', 'Joel', 'Taba', true, '2c9a43d2-8406-4860-b33b-f7ba989885ba'),
  ('96b87460-6179-483d-a6d5-9cbcacd9d06d', 'adminfleet', 'admin@fleetman.cm', 'Marie', 'Admin', true, '96b87460-6179-483d-a6d5-9cbcacd9d06d'),
  ('e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb', 'manager.dupont', 'manager1@fleetman.cm', 'Jean', 'Dupont', true, 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb'),
  ('35944e04-43c1-4eba-8acf-13f72a3ca5be', 'fleetdriver', 'driver@fleetman.cm', 'André', 'Mbarga', true, '35944e04-43c1-4eba-8acf-13f72a3ca5be')
ON CONFLICT (id) DO NOTHING;

-- 2. Manager + flottes
INSERT INTO fleet.fleet_managers (user_id, company_name)
VALUES ('e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb', 'Transport Express CM')
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO fleet.fleets (id, manager_id, name, phone_number, kernel_organization_id)
VALUES
  ('b0000001-0000-4000-8000-000000000001', 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb', 'Flotte Yaoundé', '+237677000001', '5e69f5c5-1f03-41cb-a4a3-d59188f73323'),
  ('b0000002-0000-4000-8000-000000000002', 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb', 'Flotte Douala', '+237677000002', '5e69f5c5-1f03-41cb-a4a3-d59188f73323')
ON CONFLICT (id) DO NOTHING;

-- Lier les flottes existantes à l'org Kernel FleetMan Cameroun (idempotent)
UPDATE fleet.fleets
SET kernel_organization_id = '5e69f5c5-1f03-41cb-a4a3-d59188f73323'
WHERE kernel_organization_id IS NULL;

-- 3. Véhicules (type TRUCK / CAR via sous-requête)
INSERT INTO fleet.vehicles (id, fleet_id, manager_id, license_plate, brand, model, manufacturing_year, color, status, vehicle_type_id)
SELECT 'c0000001-0000-4000-8000-000000000001', 'b0000001-0000-4000-8000-000000000001', 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb',
       'LT-892-CE', 'Toyota', 'Hilux', 2022, 'Bleu', 'ON_TRIP', vt.id
FROM fleet.vehicle_types vt WHERE vt.code = 'TRUCK' LIMIT 1
ON CONFLICT (id) DO NOTHING;

INSERT INTO fleet.vehicles (id, fleet_id, manager_id, license_plate, brand, model, manufacturing_year, color, status, vehicle_type_id)
SELECT 'c0000002-0000-4000-8000-000000000002', 'b0000002-0000-4000-8000-000000000002', 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb',
       'CE-456-AB', 'Mercedes', 'Actros', 2020, 'Blanc', 'AVAILABLE', vt.id
FROM fleet.vehicle_types vt WHERE vt.code = 'TRUCK' LIMIT 1
ON CONFLICT (id) DO NOTHING;

INSERT INTO fleet.vehicles (id, fleet_id, manager_id, license_plate, brand, model, manufacturing_year, color, status, vehicle_type_id)
SELECT 'c0000003-0000-4000-8000-000000000003', 'b0000001-0000-4000-8000-000000000001', 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb',
       'SW-123-DL', 'Toyota', 'Corolla', 2021, 'Gris', 'MAINTENANCE', vt.id
FROM fleet.vehicle_types vt WHERE vt.code = 'CAR' LIMIT 1
ON CONFLICT (id) DO NOTHING;

-- 4. Conducteur
INSERT INTO fleet.drivers (user_id, fleet_id, licence_number, status, assigned_vehicle_id)
VALUES ('35944e04-43c1-4eba-8acf-13f72a3ca5be', 'b0000001-0000-4000-8000-000000000001', 'B1234567CM', 'ACTIVE', 'c0000001-0000-4000-8000-000000000001')
ON CONFLICT (user_id) DO NOTHING;

UPDATE fleet.vehicles SET current_driver_id = '35944e04-43c1-4eba-8acf-13f72a3ca5be'
WHERE id = 'c0000001-0000-4000-8000-000000000001';

-- 5. Paramètres véhicule
INSERT INTO fleet.operational_parameters (id, vehicle_id, mileage, fuel_level, status)
VALUES
  ('ca000001-0000-4000-8000-000000000001', 'c0000001-0000-4000-8000-000000000001', 87420, '62', true),
  ('ca000002-0000-4000-8000-000000000002', 'c0000002-0000-4000-8000-000000000002', 120500, '45', true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO fleet.financial_parameters (id, vehicle_id, insurance_number, insurance_expired_at, cost_per_km)
VALUES
  ('cb000001-0000-4000-8000-000000000001', 'c0000001-0000-4000-8000-000000000001', 'ASS-2024-8891', '2026-12-15', 425),
  ('cb000002-0000-4000-8000-000000000002', 'c0000002-0000-4000-8000-000000000002', 'ASS-2023-4412', '2026-06-20', 412)
ON CONFLICT (id) DO NOTHING;

INSERT INTO fleet.maintenance_parameters (id, vehicle_id, engine_status, battery_health, maintenance_status)
VALUES
  ('cc000001-0000-4000-8000-000000000001', 'c0000001-0000-4000-8000-000000000001', 'OK', 88, 'UP_TO_DATE'),
  ('cc000002-0000-4000-8000-000000000002', 'c0000003-0000-4000-8000-000000000003', 'NEEDS_SERVICE', 72, 'PENDING')
ON CONFLICT (id) DO NOTHING;

-- 6. Trajets (statuts alignés migration 021 : DEPARTED remplace ONGOING)
INSERT INTO fleet.trips (id, trip_code, vehicle_id, driver_id, fleet_id, created_by, start_date, end_date, start_time, end_time, status, distance_km, duration_minutes)
VALUES
  ('d0000001-0000-4000-8000-000000000001', 'TRJ-2026-0001', 'c0000001-0000-4000-8000-000000000001', '35944e04-43c1-4eba-8acf-13f72a3ca5be',
   'b0000001-0000-4000-8000-000000000001', 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb',
   '2026-06-04', '2026-06-04', '08:15', '12:40', 'COMPLETED', 124, 265),
  ('d0000002-0000-4000-8000-000000000002', 'TRJ-2026-0002', 'c0000003-0000-4000-8000-000000000003', '35944e04-43c1-4eba-8acf-13f72a3ca5be',
   'b0000001-0000-4000-8000-000000000001', 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb',
   '2026-06-03', '2026-06-03', '14:00', '18:30', 'COMPLETED', 89, 270),
  ('d0000003-0000-4000-8000-000000000003', 'TRJ-2026-0003', 'c0000001-0000-4000-8000-000000000001', '35944e04-43c1-4eba-8acf-13f72a3ca5be',
   'b0000001-0000-4000-8000-000000000001', 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb',
   CURRENT_DATE, NULL, '10:30', NULL, 'DEPARTED', NULL, NULL)
ON CONFLICT (id) DO NOTHING;

-- 7. Géofences
INSERT INTO fleet.geofence_zones (id, manager_id, fleet_id, zone_type)
VALUES
  ('e0000001-0000-4000-8000-000000000001', 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb', 'b0000001-0000-4000-8000-000000000001', 'CIRCLE'),
  ('e0000002-0000-4000-8000-000000000002', 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb', 'b0000002-0000-4000-8000-000000000002', 'CIRCLE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO fleet.geofence_events (id, vehicle_id, zone_id, type)
VALUES
  ('e1000001-0000-4000-8000-000000000001', 'c0000001-0000-4000-8000-000000000001', 'e0000001-0000-4000-8000-000000000001', 'EXIT'),
  ('e1000002-0000-4000-8000-000000000002', 'c0000002-0000-4000-8000-000000000002', 'e0000002-0000-4000-8000-000000000002', 'ENTRY')
ON CONFLICT (id) DO NOTHING;

-- 8. Opérations
INSERT INTO fleet.maintenances (id, subject, cost, vehicle_id, vehicle_registration, driver_id, driver_full_name)
VALUES ('f1000001-0000-4000-8000-000000000001', 'Vidange + filtres', 85000, 'c0000003-0000-4000-8000-000000000003', 'SW-123-DL', '35944e04-43c1-4eba-8acf-13f72a3ca5be', 'André Mbarga')
ON CONFLICT (id) DO NOTHING;

INSERT INTO fleet.incidents (id, type, description, severity, vehicle_id, vehicle_registration, driver_id, driver_full_name, status)
VALUES ('f1000002-0000-4000-8000-000000000002', 'ACCIDENT', 'Rayure latérale parking', 'MEDIUM', 'c0000001-0000-4000-8000-000000000001', 'LT-892-CE', '35944e04-43c1-4eba-8acf-13f72a3ca5be', 'André Mbarga', 'REPORTED')
ON CONFLICT (id) DO NOTHING;

INSERT INTO fleet.fuel_recharges (id, quantity, price, station_name, vehicle_id, vehicle_registration, driver_id, driver_full_name)
VALUES ('f1000003-0000-4000-8000-000000000003', 65, 42250, 'TOTAL', 'c0000001-0000-4000-8000-000000000001', 'LT-892-CE', '35944e04-43c1-4eba-8acf-13f72a3ca5be', 'André Mbarga')
ON CONFLICT (id) DO NOTHING;

-- 9. Documents
INSERT INTO fleet.vehicle_documents (id, vehicle_id, doc_type, doc_number, expiry_date, status)
VALUES
  ('f2000001-0000-4000-8000-000000000001', 'c0000001-0000-4000-8000-000000000001', 'INSURANCE', 'ASS-2024-8891', '2026-12-15', 'VALID'),
  ('f2000002-0000-4000-8000-000000000002', 'c0000002-0000-4000-8000-000000000002', 'TECHNICAL_CONTROL', 'VT-2025-112', '2026-06-20', 'EXPIRING_SOON')
ON CONFLICT (id) DO NOTHING;

INSERT INTO fleet.driver_documents (id, driver_id, doc_type, doc_number, expiry_date, status)
VALUES ('f2000003-0000-4000-8000-000000000003', '35944e04-43c1-4eba-8acf-13f72a3ca5be', 'DRIVING_LICENSE', 'B1234567CM', '2027-03-10', 'VALID')
ON CONFLICT (id) DO NOTHING;

-- 10. Plannings & affectations
INSERT INTO fleet.schedules (id, fleet_id, manager_id, title, period_type, start_date, end_date, status)
VALUES
  ('f3000001-0000-4000-8000-000000000001', 'b0000001-0000-4000-8000-000000000001', 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb',
   'Planning semaine 10-16 Juin', 'WEEKLY', '2026-06-10', '2026-06-16', 'PUBLISHED'),
  ('f3000002-0000-4000-8000-000000000002', 'b0000002-0000-4000-8000-000000000002', 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb',
   'Brouillon Juillet', 'MONTHLY', '2026-07-01', '2026-07-31', 'DRAFT')
ON CONFLICT (id) DO NOTHING;

INSERT INTO fleet.assignments (id, schedule_id, fleet_id, vehicle_id, driver_id, start_datetime, end_datetime, status, estimated_km)
VALUES
  ('f3000003-0000-4000-8000-000000000003', 'f3000001-0000-4000-8000-000000000001', 'b0000001-0000-4000-8000-000000000001',
   'c0000001-0000-4000-8000-000000000001', '35944e04-43c1-4eba-8acf-13f72a3ca5be',
   '2026-06-04 08:00:00', '2026-06-04 12:00:00', 'IN_PROGRESS', 120),
  ('f3000004-0000-4000-8000-000000000004', 'f3000001-0000-4000-8000-000000000001', 'b0000001-0000-4000-8000-000000000001',
   'c0000003-0000-4000-8000-000000000003', '35944e04-43c1-4eba-8acf-13f72a3ca5be',
   '2026-06-05 14:00:00', '2026-06-05 18:00:00', 'PENDING', 85)
ON CONFLICT (id) DO NOTHING;

-- 11. KPI
INSERT INTO fleet.kpi_snapshots (id, fleet_id, entity_type, entity_id, period_type, period_start, period_end, total_km, total_trips, cost_per_km, availability_rate)
VALUES ('f4000001-0000-4000-8000-000000000001', 'b0000001-0000-4000-8000-000000000001', 'FLEET', 'b0000001-0000-4000-8000-000000000001',
        'WEEKLY', '2026-06-01', '2026-06-07', 12450, 48, 425, 87.5)
ON CONFLICT (id) DO NOTHING;

-- 12. Budgets & dépenses
INSERT INTO fleet.budgets (id, scope, entity_id, manager_id, budget_month, amount, consumed, alert_level)
VALUES ('f5000001-0000-4000-8000-000000000001', 'FLEET', 'b0000001-0000-4000-8000-000000000001',
        'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb', '2026-06-01', 2500000, 422500, 'NORMAL')
ON CONFLICT (id) DO NOTHING;

INSERT INTO fleet.expenses (id, expense_type, amount, description, status, source_type, vehicle_id, vehicle_registration, fleet_id, manager_id, driver_id)
VALUES ('f5000002-0000-4000-8000-000000000002', 'FUEL', 42250, 'Plein Total Bastos', 'APPROVED', 'MANUAL',
        'c0000001-0000-4000-8000-000000000001', 'LT-892-CE', 'b0000001-0000-4000-8000-000000000001',
        'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb', '35944e04-43c1-4eba-8acf-13f72a3ca5be')
ON CONFLICT (id) DO NOTHING;

-- 13. Score conducteur
INSERT INTO fleet.driver_scores (id, driver_id, fleet_id, manager_id, period_type, period_start, period_end, total_trips, final_score, badge)
VALUES ('f6000001-0000-4000-8000-000000000001', '35944e04-43c1-4eba-8acf-13f72a3ca5be', 'b0000001-0000-4000-8000-000000000001',
        'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb', 'MONTHLY', '2026-06-01', '2026-06-30', 48, 92, 'EXCELLENCE')
ON CONFLICT (id) DO NOTHING;

-- 14. Notifications
INSERT INTO fleet.notification_settings (user_id, enable_email, enable_push)
VALUES
  ('e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb', true, true),
  ('35944e04-43c1-4eba-8acf-13f72a3ca5be', true, true)
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO fleet.notifications (id, user_id, title, message, type, is_read)
VALUES
  ('f7000001-0000-4000-8000-000000000001', 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb', 'Incident signalé', 'Rayure véhicule LT-892-CE', 'WARNING', false),
  ('f7000002-0000-4000-8000-000000000002', '35944e04-43c1-4eba-8acf-13f72a3ca5be', 'Affectation confirmée', 'Créneau demain 14h-18h', 'INFO', false)
ON CONFLICT (id) DO NOTHING;

-- 15. Abonnement démo (plan Starter — max 5 véhicules, aligné migration 026)
UPDATE fleet.fleet_managers
SET plan_id = '55000001-0000-4000-8000-000000000001',
    subscription_status = 'ACTIVE',
    subscription_start = CURRENT_DATE,
    subscription_end = CURRENT_DATE + INTERVAL '1 year'
WHERE user_id = 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb';
