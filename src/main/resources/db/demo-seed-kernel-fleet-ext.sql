-- Extension seed démo : 2 managers, 5 chauffeurs, 5 véhicules (profil local,demo + Kernel)
-- Idempotent — complète demo-seed.sql

INSERT INTO fleet.users (id, username, email, first_name, last_name, is_active, kernel_id)
VALUES
  ('f2000002-0000-4000-8000-000000000002', 'manager.foka', 'manager2@fleetman.cm', 'Paul', 'Foka', true, 'f282a93e-4469-4ac2-b7a9-53ab2433a178'),
  ('a1000001-0000-4000-8000-000000000001', 'driver.kouam', 'driver2@fleetman.cm', 'Paul', 'Kouam', true, 'b62254a8-5702-441f-91bc-3e7b137f1d4f'),
  ('a1000002-0000-4000-8000-000000000002', 'driver.nana', 'driver3@fleetman.cm', 'Amina', 'Nana', true, '6fa0d4ca-14ba-4691-b16b-c2acf1888b73'),
  ('a1000003-0000-4000-8000-000000000003', 'driver.fouda', 'driver4@fleetman.cm', 'Eric', 'Fouda', true, 'e8b7bccd-9d24-4878-969e-3323058de866'),
  ('a1000004-0000-4000-8000-000000000004', 'driver.bella', 'driver5@fleetman.cm', 'Claire', 'Bella', true, '2c991c31-c68d-426a-934d-dd4d20e70093')
ON CONFLICT (id) DO UPDATE SET kernel_id = EXCLUDED.kernel_id, email = EXCLUDED.email;

INSERT INTO fleet.fleet_managers (user_id, company_name)
VALUES ('f2000002-0000-4000-8000-000000000002', 'Logistique Foka SARL')
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO fleet.fleets (id, manager_id, name, phone_number, kernel_organization_id)
VALUES
  ('b0000003-0000-4000-8000-000000000003', 'f2000002-0000-4000-8000-000000000002', 'Flotte Garoua', '+237677000103', '5e69f5c5-1f03-41cb-a4a3-d59188f73323')
ON CONFLICT (id) DO NOTHING;

INSERT INTO fleet.vehicles (id, fleet_id, manager_id, license_plate, brand, model, manufacturing_year, color, status, vehicle_type_id)
SELECT 'c0000004-0000-4000-8000-000000000004', 'b0000001-0000-4000-8000-000000000001', 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb',
       'LT-445-CE', 'Isuzu', 'NPR', 2019, 'Jaune', 'AVAILABLE', vt.id
FROM fleet.vehicle_types vt WHERE vt.code = 'TRUCK' LIMIT 1
ON CONFLICT (id) DO NOTHING;

INSERT INTO fleet.vehicles (id, fleet_id, manager_id, license_plate, brand, model, manufacturing_year, color, status, vehicle_type_id)
SELECT 'c0000005-0000-4000-8000-000000000005', 'b0000002-0000-4000-8000-000000000002', 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb',
       'CE-789-AB', 'Hyundai', 'H100', 2021, 'Blanc', 'AVAILABLE', vt.id
FROM fleet.vehicle_types vt WHERE vt.code = 'TRUCK' LIMIT 1
ON CONFLICT (id) DO NOTHING;

INSERT INTO fleet.drivers (user_id, fleet_id, licence_number, status, assigned_vehicle_id)
VALUES
  ('a1000001-0000-4000-8000-000000000001', 'b0000001-0000-4000-8000-000000000001', 'B2000001CM', 'ACTIVE', 'c0000004-0000-4000-8000-000000000004'),
  ('a1000002-0000-4000-8000-000000000002', 'b0000001-0000-4000-8000-000000000001', 'B2000002CM', 'ACTIVE', NULL),
  ('a1000003-0000-4000-8000-000000000003', 'b0000001-0000-4000-8000-000000000001', 'B2000003CM', 'ACTIVE', NULL),
  ('a1000004-0000-4000-8000-000000000004', 'b0000002-0000-4000-8000-000000000002', 'B2000004CM', 'ACTIVE', 'c0000005-0000-4000-8000-000000000005')
ON CONFLICT (user_id) DO NOTHING;

UPDATE fleet.vehicles SET current_driver_id = 'a1000004-0000-4000-8000-000000000004'
WHERE id = 'c0000004-0000-4000-8000-000000000004';

UPDATE fleet.vehicles SET current_driver_id = 'a1000004-0000-4000-8000-000000000004'
WHERE id = 'c0000005-0000-4000-8000-000000000005';
