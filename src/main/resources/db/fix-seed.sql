-- ============================================================
--  FleetMan — Script de correction / complément du seed
--  Insère les données manquantes avec les bons UUIDs
-- ============================================================

-- ═══════════════════════════════════════════════════════════════
-- 1. Véhicules manquants (c4, c5, c6) — UUIDs vehicle_types corrects
--    VAN  = 48b8e1bf-3551-4c24-ba7a-4b454e5d18f0
--    PICKUP = e144bbf4-e50a-4974-8b5d-e51bb737a9c3
-- ═══════════════════════════════════════════════════════════════
INSERT INTO fleet.vehicles (id, fleet_id, manager_id, vehicle_type_id, license_plate, brand, model, manufacturing_year, color, status) VALUES
  ('c0000004-0000-4000-8000-000000000004', 'b0000002-0000-4000-8000-000000000002', 'a0000003-0000-4000-8000-000000000003',
   '48b8e1bf-3551-4c24-ba7a-4b454e5d18f0', 'DL-001-YA', 'Renault',    'Master',  2023, 'Blanc', 'AVAILABLE'),
  ('c0000005-0000-4000-8000-000000000005', 'b0000003-0000-4000-8000-000000000003', 'a0000006-0000-4000-8000-000000000006',
   '48b8e1bf-3551-4c24-ba7a-4b454e5d18f0', 'BF-200-CM', 'Ford',       'Transit', 2022, 'Gris',  'AVAILABLE'),
  ('c0000006-0000-4000-8000-000000000006', 'b0000001-0000-4000-8000-000000000001', 'a0000003-0000-4000-8000-000000000003',
   'e144bbf4-e50a-4974-8b5d-e51bb737a9c3', 'YA-777-LT', 'Mitsubishi', 'L200',    2021, 'Rouge', 'AVAILABLE')
ON CONFLICT (id) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════
-- 2. Paramètres des nouveaux véhicules
-- ═══════════════════════════════════════════════════════════════
INSERT INTO fleet.operational_parameters (id, vehicle_id, mileage, fuel_level, status, current_speed, odometer_reading) VALUES
  ('ca000004-0000-4000-8000-000000000004', 'c0000004-0000-4000-8000-000000000004', 32100, '80', true, 0, 32100),
  ('ca000005-0000-4000-8000-000000000005', 'c0000005-0000-4000-8000-000000000005', 15200, '90', true, 0, 15200),
  ('ca000006-0000-4000-8000-000000000006', 'c0000006-0000-4000-8000-000000000006', 68000, '55', true, 0, 68000)
ON CONFLICT (id) DO NOTHING;

INSERT INTO fleet.financial_parameters (id, vehicle_id, insurance_number, insurance_expired_at, cost_per_km, registered_at, purchased_at, depreciation_rate) VALUES
  ('cb000004-0000-4000-8000-000000000004', 'c0000004-0000-4000-8000-000000000004', 'ASS-2025-0071', '2027-01-20', 380, '2023-02-01', '2023-01-25', 10),
  ('cb000005-0000-4000-8000-000000000005', 'c0000005-0000-4000-8000-000000000005', 'ASS-2025-0099', '2027-03-10', 350, '2022-07-01', '2022-06-15', 12),
  ('cb000006-0000-4000-8000-000000000006', 'c0000006-0000-4000-8000-000000000006', 'ASS-2024-5500', '2026-11-30', 390, '2021-10-01', '2021-09-20', 14)
ON CONFLICT (id) DO NOTHING;

INSERT INTO fleet.maintenance_parameters (id, vehicle_id, engine_status, battery_health, maintenance_status, last_maintenance_at, next_maintenance_at) VALUES
  ('cc000004-0000-4000-8000-000000000004', 'c0000004-0000-4000-8000-000000000004', 'OK',            95, 'UP_TO_DATE', '2026-04-20', '2026-10-20'),
  ('cc000005-0000-4000-8000-000000000005', 'c0000005-0000-4000-8000-000000000005', 'OK',            98, 'UP_TO_DATE', '2026-05-01', '2026-11-01'),
  ('cc000006-0000-4000-8000-000000000006', 'c0000006-0000-4000-8000-000000000006', 'NEEDS_SERVICE', 55, 'OVERDUE',    '2025-09-01', '2026-03-01')
ON CONFLICT (id) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════
-- 3. Trajets manquants (référençant c4 et c6)
-- ═══════════════════════════════════════════════════════════════
INSERT INTO fleet.trips (id, vehicle_id, driver_id, start_date, end_date, start_time, end_time, status, distance_km, duration_minutes) VALUES
  ('d0000004-0000-4000-8000-000000000004', 'c0000002-0000-4000-8000-000000000002', 'a0000005-0000-4000-8000-000000000005',
   '2026-06-04', '2026-06-04', '06:00', '14:30', 'COMPLETED', 312, 510),
  ('d0000005-0000-4000-8000-000000000005', 'c0000004-0000-4000-8000-000000000004', 'a0000005-0000-4000-8000-000000000005',
   '2026-06-05', '2026-06-05', '08:00', '12:15', 'COMPLETED', 89, 255),
  ('d0000006-0000-4000-8000-000000000006', 'c0000006-0000-4000-8000-000000000006', 'a0000004-0000-4000-8000-000000000004',
   '2026-06-10', NULL, '07:00', NULL, 'SCHEDULED', NULL, NULL),
  ('d0000007-0000-4000-8000-000000000007', 'c0000001-0000-4000-8000-000000000001', 'a0000004-0000-4000-8000-000000000004',
   '2026-05-15', '2026-05-15', '14:00', '17:00', 'COMPLETED', 55, 180),
  ('d0000008-0000-4000-8000-000000000008', 'c0000002-0000-4000-8000-000000000002', 'a0000005-0000-4000-8000-000000000005',
   '2026-06-07', '2026-06-07', '09:30', '16:00', 'COMPLETED', 204, 390)
ON CONFLICT (id) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════
-- 4. Opérations manquantes (maintenance, incident, carburant)
-- ═══════════════════════════════════════════════════════════════
INSERT INTO fleet.maintenances (id, subject, cost, date_time, report, vehicle_id, vehicle_registration, driver_id, driver_full_name, location_name) VALUES
  ('f1000004-0000-4000-8000-000000000004', 'Remplacement pneus avant',  120000, '2026-05-20 14:00:00',
   'Deux pneus avant usés remplacés par Michelin 205/65R15.',
   'c0000002-0000-4000-8000-000000000002', 'CE-456-AB', 'a0000005-0000-4000-8000-000000000005', 'Sophie Nguema', 'Garage Douala Centre'),
  ('f1000005-0000-4000-8000-000000000005', 'Révision 80 000 km',        250000, '2026-03-15 08:00:00',
   'Révision complète : plaquettes, bougies, courroie de distribution.',
   'c0000001-0000-4000-8000-000000000001', 'LT-892-CE', 'a0000004-0000-4000-8000-000000000004', 'André Mbarga', 'Concessionnaire Toyota Yaoundé'),
  ('f1000006-0000-4000-8000-000000000006', 'Réparation climatisation',   45000, '2026-04-10 10:00:00',
   'Recharge gaz climatisation + remplacement filtre habitacle.',
   'c0000004-0000-4000-8000-000000000004', 'DL-001-YA', NULL, NULL, 'Froid Expert Douala')
ON CONFLICT (id) DO NOTHING;

INSERT INTO fleet.incidents (id, type, description, severity, incident_date_time, cost, status, vehicle_id, vehicle_registration, driver_id, driver_full_name, reported_by) VALUES
  ('f1000007-0000-4000-8000-000000000007', 'BREAKDOWN', 'Crevaison pneu arrière gauche sur l''autoroute', 'LOW',
   '2026-05-29 08:45:00', 8000, 'RESOLVED',
   'c0000002-0000-4000-8000-000000000002', 'CE-456-AB', 'a0000005-0000-4000-8000-000000000005', 'Sophie Nguema', 'Sophie Nguema'),
  ('f1000008-0000-4000-8000-000000000008', 'TRAFFIC_VIOLATION', 'Excès de vitesse boulevard de la Réunification', 'LOW',
   '2026-05-25 14:20:00', 25000, 'CLOSED',
   'c0000003-0000-4000-8000-000000000003', 'SW-123-DL', 'a0000004-0000-4000-8000-000000000004', 'André Mbarga', 'Police routière'),
  ('f1000009-0000-4000-8000-000000000009', 'ACCIDENT', 'Collision légère à un carrefour, dommages mineurs', 'HIGH',
   '2026-06-05 17:00:00', 180000, 'UNDER_INVESTIGATION',
   'c0000006-0000-4000-8000-000000000006', 'YA-777-LT', 'a0000004-0000-4000-8000-000000000004', 'André Mbarga', 'André Mbarga')
ON CONFLICT (id) DO NOTHING;

INSERT INTO fleet.fuel_recharges (id, quantity, price, recharge_date_time, station_name, vehicle_id, vehicle_registration, driver_id, driver_full_name) VALUES
  ('f1000010-0000-4000-8000-000000000010', 80, 52000, '2026-05-30 16:30:00', 'SHELL',   'c0000002-0000-4000-8000-000000000002', 'CE-456-AB', 'a0000005-0000-4000-8000-000000000005', 'Sophie Nguema'),
  ('f1000011-0000-4000-8000-000000000011', 45, 29250, '2026-06-03 08:15:00', 'OILIBYA', 'c0000003-0000-4000-8000-000000000003', 'SW-123-DL', 'a0000004-0000-4000-8000-000000000004', 'André Mbarga'),
  ('f1000012-0000-4000-8000-000000000012', 55, 35750, '2026-06-05 11:00:00', 'TOTAL',   'c0000004-0000-4000-8000-000000000004', 'DL-001-YA', 'a0000005-0000-4000-8000-000000000005', 'Sophie Nguema'),
  ('f1000013-0000-4000-8000-000000000013', 70, 45500, '2026-06-06 06:30:00', 'SHELL',   'c0000001-0000-4000-8000-000000000001', 'LT-892-CE', 'a0000004-0000-4000-8000-000000000004', 'André Mbarga'),
  ('f1000014-0000-4000-8000-000000000014', 40, 26000, '2026-06-04 09:00:00', 'CAMRAIL', 'c0000006-0000-4000-8000-000000000006', 'YA-777-LT', 'a0000004-0000-4000-8000-000000000004', 'André Mbarga')
ON CONFLICT (id) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════
-- 5. Documents manquants (véhicules c4, c5, c6)
-- ═══════════════════════════════════════════════════════════════
INSERT INTO fleet.vehicle_documents (id, vehicle_id, doc_type, doc_number, issuer, issue_date, expiry_date, status, notes) VALUES
  ('f2000005-0000-4000-8000-000000000005', 'c0000004-0000-4000-8000-000000000004', 'REGISTRATION',      'CE-REG-4411',   'DGTCFM',                  '2023-02-01', '2028-02-01', 'VALID',         NULL),
  ('f2000007-0000-4000-8000-000000000007', 'c0000005-0000-4000-8000-000000000005', 'INSURANCE',         'ASS-2025-0099', 'Activa Insurance',        '2025-03-10', '2027-03-10', 'VALID',         NULL),
  ('f2000008-0000-4000-8000-000000000008', 'c0000006-0000-4000-8000-000000000006', 'TRANSPORT_PERMIT',  'TP-2024-0441',  'MINT Cameroun',           '2024-01-01', '2026-05-10', 'EXPIRED',       'Permis de transport expiré !'),
  ('f2000009-0000-4000-8000-000000000009', 'c0000002-0000-4000-8000-000000000002', 'TAX_STICKER',       'VS-2026-CE456', 'DGI Douala',              '2026-01-01', '2026-12-31', 'VALID',         NULL)
ON CONFLICT (id) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════
-- 6. Dépenses manquantes
-- ═══════════════════════════════════════════════════════════════
INSERT INTO fleet.expenses (id, expense_type, amount, description, status, source_type, vehicle_id, vehicle_registration, fleet_id, manager_id, driver_id, driver_full_name) VALUES
  ('f5000007-0000-4000-8000-000000000007', 'FUEL',        52000,  'Plein Shell Bonanjo',          'APPROVED', 'MANUAL', 'c0000002-0000-4000-8000-000000000002', 'CE-456-AB', 'b0000002-0000-4000-8000-000000000002', 'a0000003-0000-4000-8000-000000000003', 'a0000005-0000-4000-8000-000000000005', 'Sophie Nguema'),
  ('f5000008-0000-4000-8000-000000000008', 'MAINTENANCE',  85000,  'Vidange + filtres SW-123-DL',  'APPROVED', 'AUTO',   'c0000003-0000-4000-8000-000000000003', 'SW-123-DL', 'b0000001-0000-4000-8000-000000000001', 'a0000003-0000-4000-8000-000000000003', NULL, NULL),
  ('f5000009-0000-4000-8000-000000000009', 'INCIDENT',     15000,  'Réparation rayure LT-892-CE',  'PENDING',  'MANUAL', 'c0000001-0000-4000-8000-000000000001', 'LT-892-CE', 'b0000001-0000-4000-8000-000000000001', 'a0000003-0000-4000-8000-000000000003', 'a0000004-0000-4000-8000-000000000004', 'André Mbarga'),
  ('f5000010-0000-4000-8000-000000000010', 'MAINTENANCE', 120000,  'Remplacement pneus CE-456-AB', 'APPROVED', 'AUTO',   'c0000002-0000-4000-8000-000000000002', 'CE-456-AB', 'b0000002-0000-4000-8000-000000000002', 'a0000003-0000-4000-8000-000000000003', NULL, NULL),
  ('f5000011-0000-4000-8000-000000000011', 'FINE',         25000,  'PV excès vitesse SW-123-DL',   'APPROVED', 'MANUAL', 'c0000003-0000-4000-8000-000000000003', 'SW-123-DL', 'b0000001-0000-4000-8000-000000000001', 'a0000003-0000-4000-8000-000000000003', 'a0000004-0000-4000-8000-000000000004', 'André Mbarga'),
  ('f5000012-0000-4000-8000-000000000012', 'FUEL',         29250,  'Plein Oilibya avant mission',  'APPROVED', 'MANUAL', 'c0000003-0000-4000-8000-000000000003', 'SW-123-DL', 'b0000001-0000-4000-8000-000000000001', 'a0000003-0000-4000-8000-000000000003', 'a0000004-0000-4000-8000-000000000004', 'André Mbarga'),
  ('f5000013-0000-4000-8000-000000000013', 'MAINTENANCE', 250000,  'Révision 80 000 km LT-892-CE', 'APPROVED', 'AUTO',   'c0000001-0000-4000-8000-000000000001', 'LT-892-CE', 'b0000001-0000-4000-8000-000000000001', 'a0000003-0000-4000-8000-000000000003', NULL, NULL),
  ('f5000014-0000-4000-8000-000000000014', 'OTHER',        45000,  'Réparation clim DL-001-YA',    'PENDING',  'MANUAL', 'c0000004-0000-4000-8000-000000000004', 'DL-001-YA', 'b0000002-0000-4000-8000-000000000002', 'a0000003-0000-4000-8000-000000000003', NULL, NULL)
ON CONFLICT (id) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════
-- 7. Points géographiques (UUIDs 100% hex valides)
-- ═══════════════════════════════════════════════════════════════
INSERT INTO fleet.geofence_points (id, latitude, longitude) VALUES
  ('ea000001-0000-4000-8000-000000000001', 3.8480,  11.5021),
  ('ea000002-0000-4000-8000-000000000002', 4.0511,   9.7679),
  ('ea000003-0000-4000-8000-000000000003', 3.8671,  11.5220),
  ('ea000004-0000-4000-8000-000000000004', 5.4864,  10.4172)
ON CONFLICT (id) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════
-- 8. Plans de maintenance préventive (UUIDs valides : f8xxxxxx)
-- ═══════════════════════════════════════════════════════════════
INSERT INTO fleet.maintenance_plans (id, maintenance_type, scope, fleet_id, vehicle_id, manager_id, label, description, interval_km, interval_days, pre_alert_km, pre_alert_days, active) VALUES
  ('f8000001-0000-4000-8000-000000000001', 'OIL_CHANGE',         'FLEET',   'b0000001-0000-4000-8000-000000000001', NULL, 'a0000003-0000-4000-8000-000000000003',
   'Vidange tous les 10 000 km', 'Remplacement huile moteur et filtre toutes les 10 000 km ou 6 mois', 10000, 180, 1000, 15, true),
  ('f8000002-0000-4000-8000-000000000002', 'TIRE_ROTATION',      'FLEET',   'b0000001-0000-4000-8000-000000000001', NULL, 'a0000003-0000-4000-8000-000000000003',
   'Rotation pneus 20 000 km', 'Rotation des pneus toutes les 20 000 km', 20000, NULL, 2000, NULL, true),
  ('f8000003-0000-4000-8000-000000000003', 'BRAKE_INSPECTION',   'VEHICLE', 'b0000001-0000-4000-8000-000000000001', 'c0000001-0000-4000-8000-000000000001', 'a0000003-0000-4000-8000-000000000003',
   'Inspection freins LT-892-CE', 'Vérification plaquettes et disques', 30000, 365, 3000, 30, true),
  ('f8000004-0000-4000-8000-000000000004', 'GENERAL_INSPECTION', 'FLEET',   'b0000002-0000-4000-8000-000000000002', NULL, 'a0000003-0000-4000-8000-000000000003',
   'Révision annuelle Flotte Douala', 'Révision générale annuelle de tous les véhicules Douala', NULL, 365, NULL, 30, true),
  ('f8000005-0000-4000-8000-000000000005', 'FILTER_CHANGE',      'FLEET',   'b0000003-0000-4000-8000-000000000003', NULL, 'a0000006-0000-4000-8000-000000000006',
   'Remplacement filtres Bafoussam', 'Filtres air + habitacle toutes les 15 000 km', 15000, NULL, 1500, NULL, true)
ON CONFLICT (id) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════
-- 9. Alertes de maintenance préventive (UUIDs valides : f9xxxxxx)
-- ═══════════════════════════════════════════════════════════════
INSERT INTO fleet.maintenance_alerts (id, plan_id, maintenance_type, vehicle_id, vehicle_registration, fleet_id, manager_id,
  status, trigger_type, last_maintenance_km, target_km, current_km, km_remaining, last_maintenance_date, target_date, days_remaining) VALUES
  ('f9000001-0000-4000-8000-000000000001', 'f8000001-0000-4000-8000-000000000001', 'OIL_CHANGE',
   'c0000003-0000-4000-8000-000000000003', 'SW-123-DL', 'b0000001-0000-4000-8000-000000000001', 'a0000003-0000-4000-8000-000000000003',
   'OVERDUE', 'BOTH', 44300, 54300, 54300, 0, '2025-11-10', '2026-05-10', -28),
  ('f9000002-0000-4000-8000-000000000002', 'f8000001-0000-4000-8000-000000000001', 'OIL_CHANGE',
   'c0000006-0000-4000-8000-000000000006', 'YA-777-LT', 'b0000001-0000-4000-8000-000000000001', 'a0000003-0000-4000-8000-000000000003',
   'DUE', 'MILEAGE', 58000, 68000, 68000, 0, '2025-09-01', NULL, NULL),
  ('f9000003-0000-4000-8000-000000000003', 'f8000002-0000-4000-8000-000000000002', 'TIRE_ROTATION',
   'c0000001-0000-4000-8000-000000000001', 'LT-892-CE', 'b0000001-0000-4000-8000-000000000001', 'a0000003-0000-4000-8000-000000000003',
   'UPCOMING', 'MILEAGE', 70000, 90000, 87420, 2580, '2026-03-15', NULL, NULL),
  ('f9000004-0000-4000-8000-000000000004', 'f8000003-0000-4000-8000-000000000003', 'BRAKE_INSPECTION',
   'c0000001-0000-4000-8000-000000000001', 'LT-892-CE', 'b0000001-0000-4000-8000-000000000001', 'a0000003-0000-4000-8000-000000000003',
   'UPCOMING', 'DATE', NULL, NULL, NULL, NULL, '2025-09-01', '2026-09-01', 85)
ON CONFLICT (vehicle_id, maintenance_type) WHERE status != 'RESOLVED' DO NOTHING;

-- ═══════════════════════════════════════════════════════════════
-- 10. Règles d'alertes métier (UUIDs valides : ab xxxxxx)
-- ═══════════════════════════════════════════════════════════════
INSERT INTO fleet.alert_rules (id, name, description, manager_id, trigger_type, action_type, target_role, active, system_template, condition_value) VALUES
  ('ab000001-0000-4000-8000-000000000001', 'Alerte doc expiration J-30',    'Notifier manager 30 jours avant expiration document',    'a0000003-0000-4000-8000-000000000003', 'DOCUMENT_EXPIRY',       'IN_APP_NOTIFICATION', 'MANAGER', true, true,  '30'),
  ('ab000002-0000-4000-8000-000000000002', 'Alerte budget 80%',              'Notifier quand budget consommé à 80%',                   'a0000003-0000-4000-8000-000000000003', 'BUDGET_THRESHOLD',      'IN_APP_NOTIFICATION', 'MANAGER', true, true,  '80'),
  ('ab000003-0000-4000-8000-000000000003', 'Alerte budget dépassé',          'Notifier quand budget dépassé (100%)',                   'a0000003-0000-4000-8000-000000000003', 'BUDGET_THRESHOLD',      'EMAIL',               'MANAGER', true, true,  '100'),
  ('ab000004-0000-4000-8000-000000000004', 'Alerte maintenance échue',       'Notifier quand entretien préventif est dû',              'a0000003-0000-4000-8000-000000000003', 'MAINTENANCE_ALERT_DUE', 'IN_APP_NOTIFICATION', 'MANAGER', true, true,  NULL),
  ('ab000005-0000-4000-8000-000000000005', 'Alerte incident signalé',        'Notifier le manager à chaque incident déclaré',         'a0000003-0000-4000-8000-000000000003', 'INCIDENT_REPORTED',     'IN_APP_NOTIFICATION', 'MANAGER', true, true,  NULL),
  ('ab000006-0000-4000-8000-000000000006', 'Score conducteur dégradé',       'Notifier si score mensuel < 70',                        'a0000003-0000-4000-8000-000000000003', 'DRIVER_SCORE_DROP',     'IN_APP_NOTIFICATION', 'MANAGER', true, false, '70'),
  ('ab000007-0000-4000-8000-000000000007', 'Anomalie carburant',             'Détection consommation anormale > 20% au-dessus moyenne','a0000003-0000-4000-8000-000000000003', 'FUEL_ANOMALY',          'IN_APP_NOTIFICATION', 'MANAGER', true, false, '20'),
  ('ab000008-0000-4000-8000-000000000008', 'Alerte doc expiration J-7',      'Alerte urgente 7 jours avant expiration',               'a0000006-0000-4000-8000-000000000006', 'DOCUMENT_EXPIRY',       'EMAIL',               'MANAGER', true, true,  '7'),
  ('ab000009-0000-4000-8000-000000000009', 'Notification chauffeur — aff.',  'Notifier chauffeur de sa prochaine affectation',        'a0000003-0000-4000-8000-000000000003', 'INCIDENT_REPORTED',     'IN_APP_NOTIFICATION', 'DRIVER',  true, false, NULL)
ON CONFLICT (id) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════
-- 11. Événements d'alertes déclenchés (UUIDs valides : ac xxxxxx)
-- ═══════════════════════════════════════════════════════════════
INSERT INTO fleet.alert_events (id, rule_id, rule_name, manager_id, trigger_type, action_type, title, message, source_entity_id, source_entity_type, read_status) VALUES
  ('ac000001-0000-4000-8000-000000000001', 'ab000001-0000-4000-8000-000000000001', 'Alerte doc expiration J-30',
   'a0000003-0000-4000-8000-000000000003', 'DOCUMENT_EXPIRY', 'IN_APP_NOTIFICATION',
   'Document bientôt expiré — CE-456-AB',
   'Le contrôle technique du véhicule CE-456-AB expire le 30/06/2026 (dans 22 jours). Pensez à le renouveler.',
   'f2000002-0000-4000-8000-000000000002', 'VEHICLE_DOCUMENT', 'UNREAD'),
  ('ac000002-0000-4000-8000-000000000002', 'ab000003-0000-4000-8000-000000000003', 'Alerte budget dépassé',
   'a0000003-0000-4000-8000-000000000003', 'BUDGET_THRESHOLD', 'EMAIL',
   'Budget Flotte Douala à 85%',
   'Le budget mensuel de la Flotte Douala est consommé à 85% (1 530 000 / 1 800 000 FCFA). Attention au dépassement.',
   'f5000003-0000-4000-8000-000000000003', 'BUDGET', 'UNREAD'),
  ('ac000003-0000-4000-8000-000000000003', 'ab000004-0000-4000-8000-000000000004', 'Alerte maintenance échue',
   'a0000003-0000-4000-8000-000000000003', 'MAINTENANCE_ALERT_DUE', 'IN_APP_NOTIFICATION',
   'Maintenance en retard — SW-123-DL',
   'La vidange du véhicule SW-123-DL est en retard de 28 jours. Planifiez l''entretien immédiatement.',
   'f9000001-0000-4000-8000-000000000001', 'MAINTENANCE_ALERT', 'UNREAD'),
  ('ac000004-0000-4000-8000-000000000004', 'ab000005-0000-4000-8000-000000000005', 'Alerte incident signalé',
   'a0000003-0000-4000-8000-000000000003', 'INCIDENT_REPORTED', 'IN_APP_NOTIFICATION',
   'Incident critique — YA-777-LT',
   'André Mbarga a signalé une collision sur le véhicule YA-777-LT. Coût estimé : 180 000 FCFA.',
   'f1000009-0000-4000-8000-000000000009', 'INCIDENT', 'UNREAD'),
  ('ac000005-0000-4000-8000-000000000005', 'ab000001-0000-4000-8000-000000000001', 'Alerte doc expiration J-30',
   'a0000003-0000-4000-8000-000000000003', 'DOCUMENT_EXPIRY', 'IN_APP_NOTIFICATION',
   'Permis de transport expiré — YA-777-LT',
   'Le permis de transport du véhicule YA-777-LT est expiré depuis le 10/05/2026. Renouvelez immédiatement.',
   'f2000008-0000-4000-8000-000000000008', 'VEHICLE_DOCUMENT', 'UNREAD')
ON CONFLICT (id) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════
-- VÉRIFICATION FINALE
-- ═══════════════════════════════════════════════════════════════
SELECT table_name, count FROM (
  SELECT 'vehicles'          AS table_name, count(*) AS count FROM fleet.vehicles           UNION ALL
  SELECT 'trips',                           count(*)           FROM fleet.trips              UNION ALL
  SELECT 'maintenances',                    count(*)           FROM fleet.maintenances       UNION ALL
  SELECT 'incidents',                       count(*)           FROM fleet.incidents          UNION ALL
  SELECT 'fuel_recharges',                  count(*)           FROM fleet.fuel_recharges     UNION ALL
  SELECT 'vehicle_documents',               count(*)           FROM fleet.vehicle_documents  UNION ALL
  SELECT 'expenses',                        count(*)           FROM fleet.expenses           UNION ALL
  SELECT 'maintenance_plans',               count(*)           FROM fleet.maintenance_plans  UNION ALL
  SELECT 'maintenance_alerts',              count(*)           FROM fleet.maintenance_alerts UNION ALL
  SELECT 'alert_rules',                     count(*)           FROM fleet.alert_rules        UNION ALL
  SELECT 'alert_events',                    count(*)           FROM fleet.alert_events       UNION ALL
  SELECT 'geofence_points',                 count(*)           FROM fleet.geofence_points
) t ORDER BY table_name;
