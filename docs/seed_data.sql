-- ============================================================
-- SEED DATA FleetMan — Données de Test Complètes
-- Base : fleetmanBD  |  Schéma : fleet
-- Adapté à la structure réelle des tables (inspectée en base)
-- Exécution : psql -d fleetmanBD -f seed_data.sql
-- ============================================================
-- IMPORTANT : l'authentification est gérée en EXTERNE (service Auth)
-- Les users ici sont des enregistrements locaux sans mot de passe.
-- Pour tester le login, utiliser le mode "fake" auth du backend.
-- ============================================================

BEGIN;

-- ── 1. USERS ─────────────────────────────────────────────────────────────────
-- Colonnes : id, username, email, first_name, last_name, photo_url, is_active
-- Pas de password_hash (auth externe), pas de roles (gérés en externe)

INSERT INTO fleet.users (id, username, email, first_name, last_name, is_active) VALUES
  ('11111111-0000-0000-0000-000000000001', 'superadmin', 'superadmin@fleetman.cm', 'Gabriel', 'Nomo', true),
  ('11111111-0000-0000-0000-000000000002', 'adminfleet', 'admin@fleetman.cm', 'Marie', 'Biya', true),
  ('22222222-0000-0000-0000-000000000001', 'manager.dupont', 'manager1@fleetman.cm', 'Jean', 'Dupont', true),
  ('22222222-0000-0000-0000-000000000002', 'manager.foka', 'manager2@fleetman.cm', 'Paul', 'Foka', true),
  ('33333333-0000-0000-0000-000000000001', 'driver.tabi', 'driver1@fleetman.cm', 'Thomas', 'Tabi', true),
  ('33333333-0000-0000-0000-000000000002', 'driver.mbarga', 'driver2@fleetman.cm', 'Samuel', 'Mbarga', true),
  ('33333333-0000-0000-0000-000000000003', 'driver.ondoa', 'driver3@fleetman.cm', 'Alain', 'Ondoa', true),
  ('33333333-0000-0000-0000-000000000004', 'driver.belinga', 'driver4@fleetman.cm', 'Michel', 'Belinga', true),
  ('33333333-0000-0000-0000-000000000005', 'driver.mvondo', 'driver5@fleetman.cm', 'Roger', 'Mvondo', true)
ON CONFLICT (id) DO NOTHING;

-- ── 2. FLEET MANAGERS ─────────────────────────────────────────────────────────
-- Colonnes : user_id, company_name

INSERT INTO fleet.fleet_managers (user_id, company_name) VALUES
  ('22222222-0000-0000-0000-000000000001', 'Trans-Yaoundé Express SARL'),
  ('22222222-0000-0000-0000-000000000002', 'Cameroun Fret & Logistique')
ON CONFLICT (user_id) DO NOTHING;

-- ── 3. RÉFÉRENTIELS ───────────────────────────────────────────────────────────
-- Colonnes : id, code, label

INSERT INTO fleet.vehicle_types (id, code, label) VALUES
  ('t0000001-0000-0000-0000-000000000001', 'CAR', 'Voiture'),
  ('t0000001-0000-0000-0000-000000000002', 'TRUCK', 'Camion'),
  ('t0000001-0000-0000-0000-000000000003', 'VAN', 'Fourgon'),
  ('t0000001-0000-0000-0000-000000000004', 'BUS', 'Bus'),
  ('t0000001-0000-0000-0000-000000000005', 'BIKE', 'Moto')
ON CONFLICT (id) DO NOTHING;

INSERT INTO fleet.manufacturers (id, code, label) VALUES
  ('m0000001-0000-0000-0000-000000000001', 'TOYOTA', 'Toyota'),
  ('m0000001-0000-0000-0000-000000000002', 'MERCEDES', 'Mercedes-Benz'),
  ('m0000001-0000-0000-0000-000000000003', 'MITSUBISHI', 'Mitsubishi'),
  ('m0000001-0000-0000-0000-000000000004', 'IVECO', 'Iveco'),
  ('m0000001-0000-0000-0000-000000000005', 'RENAULT', 'Renault')
ON CONFLICT (id) DO NOTHING;

INSERT INTO fleet.fuel_types (id, code, label) VALUES
  ('fu000001-0000-0000-0000-000000000001', 'DIESEL', 'Diesel'),
  ('fu000001-0000-0000-0000-000000000002', 'GASOLINE', 'Essence'),
  ('fu000001-0000-0000-0000-000000000003', 'LPG', 'GPL')
ON CONFLICT (id) DO NOTHING;

INSERT INTO fleet.vehicle_colors (id, code, label) VALUES
  ('c0000001-0000-0000-0000-000000000001', 'WHITE', 'Blanc'),
  ('c0000001-0000-0000-0000-000000000002', 'BLACK', 'Noir'),
  ('c0000001-0000-0000-0000-000000000003', 'BLUE', 'Bleu'),
  ('c0000001-0000-0000-0000-000000000004', 'RED', 'Rouge'),
  ('c0000001-0000-0000-0000-000000000005', 'SILVER', 'Gris argent')
ON CONFLICT (id) DO NOTHING;

INSERT INTO fleet.transmission_types (id, code, label) VALUES
  ('tr000001-0000-0000-0000-000000000001', 'MANUAL', 'Manuelle'),
  ('tr000001-0000-0000-0000-000000000002', 'AUTO', 'Automatique')
ON CONFLICT (id) DO NOTHING;

INSERT INTO fleet.usage_types (id, code, label) VALUES
  ('ut000001-0000-0000-0000-000000000001', 'TRANSPORT', 'Transport de personnes'),
  ('ut000001-0000-0000-0000-000000000002', 'CARGO', 'Transport de marchandises'),
  ('ut000001-0000-0000-0000-000000000003', 'MIXED', 'Mixte')
ON CONFLICT (id) DO NOTHING;

INSERT INTO fleet.vehicle_sizes (id, code, label) VALUES
  ('vs000001-0000-0000-0000-000000000001', 'SMALL', 'Petit'),
  ('vs000001-0000-0000-0000-000000000002', 'MEDIUM', 'Moyen'),
  ('vs000001-0000-0000-0000-000000000003', 'LARGE', 'Grand')
ON CONFLICT (id) DO NOTHING;

-- ── 4. FLOTTES ────────────────────────────────────────────────────────────────
-- Colonnes : id, manager_id, name, phone_number, created_at

INSERT INTO fleet.fleets (id, manager_id, name, phone_number, created_at) VALUES
  ('f1111111-0000-0000-0000-000000000001', '22222222-0000-0000-0000-000000000001', 'Flotte Yaoundé Centre', '+237690000011', now() - interval '6 months'),
  ('f1111111-0000-0000-0000-000000000002', '22222222-0000-0000-0000-000000000001', 'Flotte Douala Port', '+237690000012', now() - interval '4 months'),
  ('f2222222-0000-0000-0000-000000000001', '22222222-0000-0000-0000-000000000002', 'Flotte Logistique Nord', '+237690000021', now() - interval '3 months')
ON CONFLICT (id) DO NOTHING;

-- ── 5. VÉHICULES ──────────────────────────────────────────────────────────────
-- Colonnes : id, fleet_id, manager_id, license_plate, brand, model,
--            manufacturing_year, color, status, vehicle_type_id, created_at

INSERT INTO fleet.vehicles (id, fleet_id, manager_id, license_plate, brand, model, manufacturing_year, color, status, vehicle_type_id, created_at) VALUES
  ('v1111111-0000-0000-0000-000000000001', 'f1111111-0000-0000-0000-000000000001', '22222222-0000-0000-0000-000000000001', 'LT-001-AA', 'Toyota', 'Land Cruiser', 2022, 'WHITE', 'AVAILABLE', 't0000001-0000-0000-0000-000000000001', now()),
  ('v1111111-0000-0000-0000-000000000002', 'f1111111-0000-0000-0000-000000000001', '22222222-0000-0000-0000-000000000001', 'LT-002-BB', 'Toyota', 'Hilux', 2021, 'BLACK', 'AVAILABLE', 't0000001-0000-0000-0000-000000000001', now()),
  ('v1111111-0000-0000-0000-000000000003', 'f1111111-0000-0000-0000-000000000001', '22222222-0000-0000-0000-000000000001', 'LT-003-CC', 'Mercedes-Benz', 'Sprinter', 2020, 'SILVER', 'MAINTENANCE', 't0000001-0000-0000-0000-000000000003', now()),
  ('v1111111-0000-0000-0000-000000000004', 'f1111111-0000-0000-0000-000000000002', '22222222-0000-0000-0000-000000000001', 'DL-001-DD', 'Iveco', 'Daily', 2023, 'WHITE', 'AVAILABLE', 't0000001-0000-0000-0000-000000000003', now()),
  ('v1111111-0000-0000-0000-000000000005', 'f1111111-0000-0000-0000-000000000002', '22222222-0000-0000-0000-000000000001', 'DL-002-EE', 'Mitsubishi', 'Canter', 2019, 'BLUE', 'AVAILABLE', 't0000001-0000-0000-0000-000000000002', now()),
  ('v2222222-0000-0000-0000-000000000001', 'f2222222-0000-0000-0000-000000000001', '22222222-0000-0000-0000-000000000002', 'BN-001-FF', 'Renault', 'Master', 2021, 'WHITE', 'AVAILABLE', 't0000001-0000-0000-0000-000000000003', now()),
  ('v2222222-0000-0000-0000-000000000002', 'f2222222-0000-0000-0000-000000000001', '22222222-0000-0000-0000-000000000002', 'BN-002-GG', 'Toyota', 'Dyna', 2020, 'RED', 'AVAILABLE', 't0000001-0000-0000-0000-000000000002', now())
ON CONFLICT (id) DO NOTHING;

-- ── 6. PARAMÈTRES OPÉRATIONNELS ───────────────────────────────────────────────
-- Colonnes : id, vehicle_id, status, fuel_level, mileage, odometer_reading, timestamp

INSERT INTO fleet.operational_parameters (id, vehicle_id, status, fuel_level, mileage, odometer_reading, timestamp) VALUES
  ('op111111-0000-0000-0000-000000000001', 'v1111111-0000-0000-0000-000000000001', true, '75%', 45230.5, 45230.5, now()),
  ('op111111-0000-0000-0000-000000000002', 'v1111111-0000-0000-0000-000000000002', true, '50%', 32100.0, 32100.0, now()),
  ('op111111-0000-0000-0000-000000000003', 'v1111111-0000-0000-0000-000000000003', false, '30%', 78450.0, 78450.0, now()),
  ('op111111-0000-0000-0000-000000000004', 'v1111111-0000-0000-0000-000000000004', true, '90%', 12300.0, 12300.0, now()),
  ('op111111-0000-0000-0000-000000000005', 'v1111111-0000-0000-0000-000000000005', true, '60%', 55600.0, 55600.0, now()),
  ('op222222-0000-0000-0000-000000000001', 'v2222222-0000-0000-0000-000000000001', true, '80%', 28900.0, 28900.0, now()),
  ('op222222-0000-0000-0000-000000000002', 'v2222222-0000-0000-0000-000000000002', true, '45%', 41200.0, 41200.0, now())
ON CONFLICT (id) DO NOTHING;

-- ── 7. PARAMÈTRES FINANCIERS ──────────────────────────────────────────────────
-- Colonnes : id, vehicle_id, insurance_number, insurance_expired_at,
--            purchased_at, cost_per_km

INSERT INTO fleet.financial_parameters (id, vehicle_id, insurance_number, insurance_expired_at, purchased_at, cost_per_km) VALUES
  ('fp111111-0000-0000-0000-000000000001', 'v1111111-0000-0000-0000-000000000001', 'ASS-2024-001', '2027-06-30', '2022-03-15', 250),
  ('fp111111-0000-0000-0000-000000000002', 'v1111111-0000-0000-0000-000000000002', 'ASS-2024-002', '2026-12-31', '2021-07-20', 220),
  ('fp111111-0000-0000-0000-000000000003', 'v1111111-0000-0000-0000-000000000003', 'ASS-2023-003', '2025-08-15', '2020-01-10', 180),
  ('fp111111-0000-0000-0000-000000000004', 'v1111111-0000-0000-0000-000000000004', 'ASS-2025-004', '2028-01-01', '2023-06-01', 200),
  ('fp111111-0000-0000-0000-000000000005', 'v1111111-0000-0000-0000-000000000005', 'ASS-2023-005', '2025-05-31', '2019-04-10', 160)
ON CONFLICT (id) DO NOTHING;

-- ── 8. PARAMÈTRES MAINTENANCE ─────────────────────────────────────────────────
-- Colonnes : id, vehicle_id, last_maintenance_at, next_maintenance_at,
--            engine_status, maintenance_status

INSERT INTO fleet.maintenance_parameters (id, vehicle_id, last_maintenance_at, next_maintenance_at, engine_status, maintenance_status) VALUES
  ('mp111111-0000-0000-0000-000000000001', 'v1111111-0000-0000-0000-000000000001', '2026-05-15', '2026-11-15', 'GOOD', 'UP_TO_DATE'),
  ('mp111111-0000-0000-0000-000000000002', 'v1111111-0000-0000-0000-000000000002', '2026-04-10', '2026-10-10', 'GOOD', 'UP_TO_DATE'),
  ('mp111111-0000-0000-0000-000000000003', 'v1111111-0000-0000-0000-000000000003', '2026-01-05', '2026-07-05', 'DEGRADED', 'DUE'),
  ('mp111111-0000-0000-0000-000000000004', 'v1111111-0000-0000-0000-000000000004', '2026-05-20', '2026-11-20', 'GOOD', 'UP_TO_DATE'),
  ('mp111111-0000-0000-0000-000000000005', 'v1111111-0000-0000-0000-000000000005', '2025-12-01', '2026-06-01', 'WARNING', 'OVERDUE')
ON CONFLICT (id) DO NOTHING;

-- ── 9. CONDUCTEURS ────────────────────────────────────────────────────────────
-- Colonnes : user_id, fleet_id, licence_number, status, assigned_vehicle_id

INSERT INTO fleet.drivers (user_id, fleet_id, licence_number, status, assigned_vehicle_id, created_at) VALUES
  ('33333333-0000-0000-0000-000000000001', 'f1111111-0000-0000-0000-000000000001', 'CM-2020-B-001234', 'ACTIVE', 'v1111111-0000-0000-0000-000000000001', now()),
  ('33333333-0000-0000-0000-000000000002', 'f1111111-0000-0000-0000-000000000001', 'CM-2019-B-005678', 'ACTIVE', 'v1111111-0000-0000-0000-000000000002', now()),
  ('33333333-0000-0000-0000-000000000003', 'f1111111-0000-0000-0000-000000000002', 'CM-2021-C-009012', 'ACTIVE', 'v1111111-0000-0000-0000-000000000004', now()),
  ('33333333-0000-0000-0000-000000000004', 'f2222222-0000-0000-0000-000000000001', 'CM-2018-B-003456', 'ACTIVE', 'v2222222-0000-0000-0000-000000000001', now()),
  ('33333333-0000-0000-0000-000000000005', 'f2222222-0000-0000-0000-000000000001', 'CM-2022-B-007890', 'ACTIVE', null, now())
ON CONFLICT (user_id) DO NOTHING;

-- ── 10. DOCUMENTS VÉHICULES ───────────────────────────────────────────────────
-- Colonnes : id, vehicle_id, doc_type, doc_number, issuer, issue_date,
--            expiry_date, status, created_at

INSERT INTO fleet.vehicle_documents (id, vehicle_id, doc_type, doc_number, issuer, issue_date, expiry_date, status, created_at) VALUES
  ('vd111111-0000-0000-0000-000000000001', 'v1111111-0000-0000-0000-000000000001', 'INSURANCE', 'POL-2024-001', 'AXA Cameroun', '2024-07-01', '2027-06-30', 'VALID', now()),
  ('vd111111-0000-0000-0000-000000000002', 'v1111111-0000-0000-0000-000000000001', 'REGISTRATION', 'CMR-LT-001-AA', 'MINTP', '2022-03-15', '2032-03-15', 'VALID', now()),
  ('vd111111-0000-0000-0000-000000000003', 'v1111111-0000-0000-0000-000000000002', 'INSURANCE', 'POL-2024-002', 'Colina Assurances', '2024-01-01', '2026-12-31', 'VALID', now()),
  ('vd111111-0000-0000-0000-000000000004', 'v1111111-0000-0000-0000-000000000003', 'INSURANCE', 'POL-2022-003', 'AXA Cameroun', '2022-08-15', current_date + interval '25 days', 'EXPIRING_SOON', now()),
  ('vd111111-0000-0000-0000-000000000005', 'v1111111-0000-0000-0000-000000000004', 'TECHNICAL_CONTROL', 'VT-2025-001', 'CEMAC Auto', '2025-01-10', '2026-01-09', 'VALID', now()),
  ('vd111111-0000-0000-0000-000000000006', 'v1111111-0000-0000-0000-000000000005', 'INSURANCE', 'POL-2023-005', 'Saar Lam', '2023-06-01', current_date - interval '8 days', 'EXPIRED', now()),
  ('vd111111-0000-0000-0000-000000000007', 'v2222222-0000-0000-0000-000000000001', 'INSURANCE', 'POL-2025-006', 'Colina Assurances', '2025-03-01', '2027-02-28', 'VALID', now()),
  ('vd111111-0000-0000-0000-000000000008', 'v2222222-0000-0000-0000-000000000002', 'REGISTRATION', 'CMR-BN-002-GG', 'MINTP', '2020-06-10', '2030-06-10', 'VALID', now())
ON CONFLICT (id) DO NOTHING;

-- ── 11. DOCUMENTS CONDUCTEURS ─────────────────────────────────────────────────
-- Colonnes : id, driver_id, doc_type, doc_number, issue_date, expiry_date, status

INSERT INTO fleet.driver_documents (id, driver_id, doc_type, doc_number, issue_date, expiry_date, status, created_at) VALUES
  ('dd111111-0000-0000-0000-000000000001', '33333333-0000-0000-0000-000000000001', 'DRIVING_LICENSE', 'PBC-2020-B-001234', '2020-05-10', '2030-05-09', 'VALID', now()),
  ('dd111111-0000-0000-0000-000000000002', '33333333-0000-0000-0000-000000000001', 'MEDICAL_CERT', 'MED-2024-001', '2024-01-15', current_date + interval '20 days', 'EXPIRING_SOON', now()),
  ('dd111111-0000-0000-0000-000000000003', '33333333-0000-0000-0000-000000000002', 'DRIVING_LICENSE', 'PBC-2019-B-005678', '2019-03-20', '2029-03-19', 'VALID', now()),
  ('dd111111-0000-0000-0000-000000000004', '33333333-0000-0000-0000-000000000003', 'DRIVING_LICENSE', 'PBC-2021-C-009012', '2021-09-05', '2031-09-04', 'VALID', now()),
  ('dd111111-0000-0000-0000-000000000005', '33333333-0000-0000-0000-000000000004', 'DRIVING_LICENSE', 'PBC-2018-B-003456', '2018-11-12', '2028-11-11', 'VALID', now()),
  ('dd111111-0000-0000-0000-000000000006', '33333333-0000-0000-0000-000000000005', 'DRIVING_LICENSE', 'PBC-2022-B-007890', '2022-06-01', '2032-05-31', 'VALID', now())
ON CONFLICT (id) DO NOTHING;

-- ── 12. INCIDENTS ─────────────────────────────────────────────────────────────

INSERT INTO fleet.incidents (id, type, description, severity, incident_date_time, cost, status, vehicle_id, vehicle_registration, driver_id, driver_full_name, reported_by) VALUES
  ('ic111111-0000-0000-0000-000000000001', 'BREAKDOWN', 'Panne moteur sur la RN1 à 50km de Yaoundé', 'HIGH', now() - interval '5 days', 85000.00, 'RESOLVED', 'v1111111-0000-0000-0000-000000000002', 'LT-002-BB', '33333333-0000-0000-0000-000000000002', 'Samuel Mbarga', 'Chauffeur Samuel Mbarga'),
  ('ic111111-0000-0000-0000-000000000002', 'TRAFFIC_VIOLATION', 'Excès de vitesse lors d''un contrôle de police', 'LOW', now() - interval '3 days', 25000.00, 'CLOSED', 'v1111111-0000-0000-0000-000000000001', 'LT-001-AA', '33333333-0000-0000-0000-000000000001', 'Thomas Tabi', 'Chauffeur Thomas Tabi'),
  ('ic111111-0000-0000-0000-000000000003', 'ACCIDENT', 'Accrochage léger dans le parking Marché Central', 'MEDIUM', now() - interval '1 day', 150000.00, 'REPORTED', 'v1111111-0000-0000-0000-000000000004', 'DL-001-DD', '33333333-0000-0000-0000-000000000003', 'Alain Ondoa', 'Chauffeur Alain Ondoa'),
  ('ic111111-0000-0000-0000-000000000004', 'BREAKDOWN', 'Crevaison sur l''autoroute Douala-Yaoundé', 'MEDIUM', now() - interval '8 days', 15000.00, 'RESOLVED', 'v1111111-0000-0000-0000-000000000005', 'DL-002-EE', '33333333-0000-0000-0000-000000000003', 'Alain Ondoa', 'Chauffeur Alain Ondoa'),
  ('ic111111-0000-0000-0000-000000000005', 'OTHER', 'Retard excessif livraison sans signalement', 'LOW', now() - interval '12 days', 0.00, 'CLOSED', 'v2222222-0000-0000-0000-000000000001', 'BN-001-FF', '33333333-0000-0000-0000-000000000004', 'Michel Belinga', 'Dispatch Central')
ON CONFLICT (id) DO NOTHING;

-- ── 13. MAINTENANCES ──────────────────────────────────────────────────────────

INSERT INTO fleet.maintenances (id, subject, cost, date_time, report, location_name, vehicle_id, vehicle_registration, driver_id, driver_full_name) VALUES
  ('mt111111-0000-0000-0000-000000000001', 'Vidange moteur + filtre à huile', 45000.00, now() - interval '10 days', 'Vidange effectuée 10W40, filtre remplacé. Prochain entretien dans 10 000 km.', 'Garage Omega Yaoundé', 'v1111111-0000-0000-0000-000000000001', 'LT-001-AA', '33333333-0000-0000-0000-000000000001', 'Thomas Tabi'),
  ('mt111111-0000-0000-0000-000000000002', 'Remplacement plaquettes de frein', 35000.00, now() - interval '7 days', 'Plaquettes avant et arrière remplacées. Freins testés OK.', 'Garage Central Yaoundé', 'v1111111-0000-0000-0000-000000000002', 'LT-002-BB', null, null),
  ('mt111111-0000-0000-0000-000000000003', 'Révision générale 80 000 km', 120000.00, now() - interval '2 days', 'Révision complète effectuée. Courroie de distribution remplacée.', 'Toyota Service Center Yaoundé', 'v1111111-0000-0000-0000-000000000003', 'LT-003-CC', null, null),
  ('mt111111-0000-0000-0000-000000000004', 'Remplacement batterie', 55000.00, now() - interval '15 days', 'Batterie défaillante remplacée par modèle 75Ah.', 'Garage Express Douala', 'v1111111-0000-0000-0000-000000000004', 'DL-001-DD', '33333333-0000-0000-0000-000000000003', 'Alain Ondoa'),
  ('mt111111-0000-0000-0000-000000000005', 'Rotation et équilibrage pneus', 20000.00, now() - interval '20 days', 'Rotation effectuée, pression ajustée.', 'Centre Pneus Douala', 'v1111111-0000-0000-0000-000000000005', 'DL-002-EE', null, null)
ON CONFLICT (id) DO NOTHING;

-- ── 14. RECHARGES CARBURANT ───────────────────────────────────────────────────

INSERT INTO fleet.fuel_recharges (id, quantity, price, recharge_date_time, station_name, vehicle_id, vehicle_registration, driver_id, driver_full_name) VALUES
  ('fr111111-0000-0000-0000-000000000001', 50.0, 37500.00, now() - interval '1 day', 'TOTAL', 'v1111111-0000-0000-0000-000000000001', 'LT-001-AA', '33333333-0000-0000-0000-000000000001', 'Thomas Tabi'),
  ('fr111111-0000-0000-0000-000000000002', 40.0, 30000.00, now() - interval '2 days', 'SHELL', 'v1111111-0000-0000-0000-000000000002', 'LT-002-BB', '33333333-0000-0000-0000-000000000002', 'Samuel Mbarga'),
  ('fr111111-0000-0000-0000-000000000003', 60.0, 45000.00, now() - interval '3 days', 'OILIBYA', 'v1111111-0000-0000-0000-000000000004', 'DL-001-DD', '33333333-0000-0000-0000-000000000003', 'Alain Ondoa'),
  ('fr111111-0000-0000-0000-000000000004', 35.0, 26250.00, now() - interval '4 days', 'TOTAL', 'v1111111-0000-0000-0000-000000000005', 'DL-002-EE', null, null),
  ('fr111111-0000-0000-0000-000000000005', 45.0, 33750.00, now() - interval '5 days', 'SHELL', 'v2222222-0000-0000-0000-000000000001', 'BN-001-FF', '33333333-0000-0000-0000-000000000004', 'Michel Belinga'),
  ('fr111111-0000-0000-0000-000000000006', 55.0, 41250.00, now() - interval '6 days', 'TOTAL', 'v2222222-0000-0000-0000-000000000002', 'BN-002-GG', '33333333-0000-0000-0000-000000000004', 'Michel Belinga'),
  ('fr111111-0000-0000-0000-000000000007', 30.0, 22500.00, now() - interval '7 days', 'OILIBYA', 'v1111111-0000-0000-0000-000000000001', 'LT-001-AA', '33333333-0000-0000-0000-000000000001', 'Thomas Tabi')
ON CONFLICT (id) DO NOTHING;

-- ── 15. PLANNINGS (SCHEDULES) ─────────────────────────────────────────────────

INSERT INTO fleet.schedules (id, fleet_id, manager_id, title, period_type, start_date, end_date, status, notes, created_at) VALUES
  ('sc111111-0000-0000-0000-000000000001', 'f1111111-0000-0000-0000-000000000001', '22222222-0000-0000-0000-000000000001', 'Planning Juin 2026 — Flotte Yaoundé', 'MONTHLY', '2026-06-01', '2026-06-30', 'PUBLISHED', 'Planning mensuel flotte Yaoundé Centre', now() - interval '5 days'),
  ('sc111111-0000-0000-0000-000000000002', 'f1111111-0000-0000-0000-000000000002', '22222222-0000-0000-0000-000000000001', 'Planning S24 — Flotte Douala', 'WEEKLY', '2026-06-09', '2026-06-15', 'DRAFT', 'Planning semaine 24 Douala Port', now() - interval '2 days'),
  ('sc222222-0000-0000-0000-000000000001', 'f2222222-0000-0000-0000-000000000001', '22222222-0000-0000-0000-000000000002', 'Planning Juin 2026 — Logistique Nord', 'MONTHLY', '2026-06-01', '2026-06-30', 'PUBLISHED', null, now() - interval '4 days')
ON CONFLICT (id) DO NOTHING;

-- ── 16. AFFECTATIONS (ASSIGNMENTS) ───────────────────────────────────────────

INSERT INTO fleet.assignments (id, schedule_id, fleet_id, vehicle_id, driver_id, start_datetime, end_datetime, status, start_location, end_location, estimated_km, created_at) VALUES
  ('as111111-0000-0000-0000-000000000001', 'sc111111-0000-0000-0000-000000000001', 'f1111111-0000-0000-0000-000000000001', 'v1111111-0000-0000-0000-000000000001', '33333333-0000-0000-0000-000000000001', now() + interval '1 hour', now() + interval '5 hours', 'PENDING', 'Yaoundé Centre', 'Bafoussam', 250.00, now()),
  ('as111111-0000-0000-0000-000000000002', 'sc111111-0000-0000-0000-000000000001', 'f1111111-0000-0000-0000-000000000001', 'v1111111-0000-0000-0000-000000000002', '33333333-0000-0000-0000-000000000002', now() + interval '2 hours', now() + interval '6 hours', 'PENDING', 'Yaoundé Mfoundi', 'Ebolowa', 180.00, now()),
  ('as111111-0000-0000-0000-000000000003', 'sc111111-0000-0000-0000-000000000001', 'f1111111-0000-0000-0000-000000000001', 'v1111111-0000-0000-0000-000000000004', '33333333-0000-0000-0000-000000000003', now() - interval '1 day', now() - interval '20 hours', 'COMPLETED', 'Douala Akwa', 'Douala Port', 25.00, now() - interval '1 day'),
  ('as111111-0000-0000-0000-000000000004', 'sc111111-0000-0000-0000-000000000002', 'f1111111-0000-0000-0000-000000000002', 'v1111111-0000-0000-0000-000000000005', '33333333-0000-0000-0000-000000000003', now() + interval '4 hours', now() + interval '8 hours', 'PENDING', 'Douala Bonanjo', 'Kribi', 170.00, now()),
  ('as222222-0000-0000-0000-000000000001', 'sc222222-0000-0000-0000-000000000001', 'f2222222-0000-0000-0000-000000000001', 'v2222222-0000-0000-0000-000000000001', '33333333-0000-0000-0000-000000000004', now() + interval '3 hours', now() + interval '9 hours', 'PENDING', 'Ngaoundéré', 'Garoua', 320.00, now()),
  ('as222222-0000-0000-0000-000000000002', 'sc222222-0000-0000-0000-000000000001', 'f2222222-0000-0000-0000-000000000001', 'v2222222-0000-0000-0000-000000000002', '33333333-0000-0000-0000-000000000005', now() - interval '2 days', now() - interval '1 day 18 hours', 'COMPLETED', 'Garoua', 'Maroua', 220.00, now() - interval '2 days')
ON CONFLICT (id) DO NOTHING;

-- ── 17. RÈGLES D'ALERTE ───────────────────────────────────────────────────────

INSERT INTO fleet.alert_rules (id, name, description, manager_id, trigger_type, action_type, target_role, active, system_template, condition_value, created_at, updated_at) VALUES
  ('ar111111-0000-0000-0000-000000000001', 'Document expirant (30j)', 'Alerte 30 jours avant expiration d''un document légal', '22222222-0000-0000-0000-000000000001', 'DOCUMENT_EXPIRY', 'IN_APP_NOTIFICATION', 'MANAGER', true, true, '30', now(), now()),
  ('ar111111-0000-0000-0000-000000000002', 'Incident déclaré', 'Notification immédiate à chaque incident déclaré', '22222222-0000-0000-0000-000000000001', 'INCIDENT_REPORTED', 'IN_APP_NOTIFICATION', 'MANAGER', true, true, null, now(), now()),
  ('ar111111-0000-0000-0000-000000000003', 'Budget à 80%', 'Alerte quand le budget mensuel atteint 80%', '22222222-0000-0000-0000-000000000001', 'BUDGET_THRESHOLD', 'IN_APP_NOTIFICATION', 'MANAGER', true, true, '80', now(), now()),
  ('ar111111-0000-0000-0000-000000000004', 'Maintenance en retard', 'Alerte maintenance DUE ou OVERDUE', '22222222-0000-0000-0000-000000000001', 'MAINTENANCE_ALERT_DUE', 'IN_APP_NOTIFICATION', 'MANAGER', true, true, null, now(), now()),
  ('ar222222-0000-0000-0000-000000000001', 'Document expirant (30j)', 'Alerte 30 jours avant expiration d''un document légal', '22222222-0000-0000-0000-000000000002', 'DOCUMENT_EXPIRY', 'IN_APP_NOTIFICATION', 'MANAGER', true, true, '30', now(), now()),
  ('ar222222-0000-0000-0000-000000000002', 'Incident déclaré', 'Notification immédiate à chaque incident déclaré', '22222222-0000-0000-0000-000000000002', 'INCIDENT_REPORTED', 'IN_APP_NOTIFICATION', 'MANAGER', true, true, null, now(), now())
ON CONFLICT (id) DO NOTHING;

-- ── 18. ÉVÉNEMENTS D'ALERTE (NOTIFICATIONS IN-APP) ────────────────────────────

INSERT INTO fleet.alert_events (id, rule_id, rule_name, manager_id, trigger_type, action_type, title, message, source_entity_id, source_entity_type, read_status, sent_at) VALUES
  ('ae111111-0000-0000-0000-000000000001', 'ar111111-0000-0000-0000-000000000002', 'Incident déclaré', '22222222-0000-0000-0000-000000000001', 'INCIDENT_REPORTED', 'IN_APP_NOTIFICATION', 'Incident déclaré — DL-001-DD', 'Un accident a été déclaré pour le véhicule DL-001-DD par le chauffeur Alain Ondoa. Coût estimé : 150 000 XAF.', 'ic111111-0000-0000-0000-000000000003', 'INCIDENT', 'UNREAD', now() - interval '1 day'),
  ('ae111111-0000-0000-0000-000000000002', 'ar111111-0000-0000-0000-000000000001', 'Document expirant (30j)', '22222222-0000-0000-0000-000000000001', 'DOCUMENT_EXPIRY', 'IN_APP_NOTIFICATION', 'Document expirant — LT-003-CC', 'L''assurance du véhicule LT-003-CC (Mercedes Sprinter) expire dans 25 jours. Pensez à la renouveler.', 'vd111111-0000-0000-0000-000000000004', 'VEHICLE_DOCUMENT', 'UNREAD', now() - interval '2 days'),
  ('ae111111-0000-0000-0000-000000000003', 'ar111111-0000-0000-0000-000000000001', 'Document expirant (30j)', '22222222-0000-0000-0000-000000000001', 'DOCUMENT_EXPIRY', 'IN_APP_NOTIFICATION', 'Document EXPIRÉ — DL-002-EE', 'L''assurance du véhicule DL-002-EE (Mitsubishi Canter) est EXPIRÉE depuis 8 jours. Action requise immédiatement.', 'vd111111-0000-0000-0000-000000000006', 'VEHICLE_DOCUMENT', 'UNREAD', now() - interval '5 days'),
  ('ae111111-0000-0000-0000-000000000004', 'ar111111-0000-0000-0000-000000000004', 'Maintenance en retard', '22222222-0000-0000-0000-000000000001', 'MAINTENANCE_ALERT_DUE', 'IN_APP_NOTIFICATION', 'Maintenance OVERDUE — DL-002-EE', 'La maintenance préventive du véhicule DL-002-EE est en retard depuis 9 jours.', 'mp111111-0000-0000-0000-000000000005', 'MAINTENANCE_ALERT', 'READ', now() - interval '9 days'),
  ('ae111111-0000-0000-0000-000000000005', 'ar111111-0000-0000-0000-000000000002', 'Incident déclaré', '22222222-0000-0000-0000-000000000001', 'INCIDENT_REPORTED', 'IN_APP_NOTIFICATION', 'Panne déclarée — LT-002-BB', 'Une panne moteur a été déclarée par Samuel Mbarga sur la RN1. Véhicule LT-002-BB immobilisé.', 'ic111111-0000-0000-0000-000000000001', 'INCIDENT', 'READ', now() - interval '5 days')
ON CONFLICT (id) DO NOTHING;

COMMIT;

-- Vérification rapide
SELECT 'fleet.users' AS table_name, count(*) FROM fleet.users
UNION ALL SELECT 'fleet.fleet_managers', count(*) FROM fleet.fleet_managers
UNION ALL SELECT 'fleet.fleets', count(*) FROM fleet.fleets
UNION ALL SELECT 'fleet.vehicles', count(*) FROM fleet.vehicles
UNION ALL SELECT 'fleet.drivers', count(*) FROM fleet.drivers
UNION ALL SELECT 'fleet.incidents', count(*) FROM fleet.incidents
UNION ALL SELECT 'fleet.maintenances', count(*) FROM fleet.maintenances
UNION ALL SELECT 'fleet.fuel_recharges', count(*) FROM fleet.fuel_recharges
UNION ALL SELECT 'fleet.schedules', count(*) FROM fleet.schedules
UNION ALL SELECT 'fleet.assignments', count(*) FROM fleet.assignments
UNION ALL SELECT 'fleet.vehicle_documents', count(*) FROM fleet.vehicle_documents
UNION ALL SELECT 'fleet.driver_documents', count(*) FROM fleet.driver_documents
UNION ALL SELECT 'fleet.alert_rules', count(*) FROM fleet.alert_rules
UNION ALL SELECT 'fleet.alert_events', count(*) FROM fleet.alert_events
ORDER BY table_name;
