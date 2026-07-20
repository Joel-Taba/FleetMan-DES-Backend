--liquibase formatted sql
--changeset fleet-users:seed-platform-admins-v1
-- 5 administrateurs plateforme (FLEET_ADMIN) pour la démo Super Admin UI

INSERT INTO fleet.users (id, username, email, first_name, last_name, phone, is_active, last_login_at, approval_status, approved_at)
VALUES
  ('96b87460-6179-483d-a6d5-9cbcacd9d06d', 'adminfleet',  'admin@fleetman.cm',  'Marie',  'Admin',  '+237690000010', true, NOW() - INTERVAL '2 hours',  'APPROVED', NOW() - INTERVAL '90 days'),
  ('a1000001-0000-4000-8000-000000000001', 'admin.kamga', 'admin2@fleetman.cm', 'Sophie', 'Kamga',  '+237690000011', true, NOW() - INTERVAL '1 day',    'APPROVED', NOW() - INTERVAL '60 days'),
  ('a1000002-0000-4000-8000-000000000002', 'admin.ndongo','admin3@fleetman.cm', 'Patrick','Ndongo', '+237690000012', true, NOW() - INTERVAL '3 days',   'APPROVED', NOW() - INTERVAL '45 days'),
  ('a1000003-0000-4000-8000-000000000003', 'admin.moussa','admin4@fleetman.cm', 'Aïcha',  'Moussa', '+237690000013', false, NOW() - INTERVAL '15 days', 'APPROVED', NOW() - INTERVAL '30 days'),
  ('a1000004-0000-4000-8000-000000000004', 'admin.fotso', 'admin5@fleetman.cm', 'Eric',   'Fotso',  '+237690000014', true, NOW() - INTERVAL '5 days',   'APPROVED', NOW() - INTERVAL '20 days')
ON CONFLICT (id) DO UPDATE SET
  username = EXCLUDED.username,
  email = EXCLUDED.email,
  first_name = EXCLUDED.first_name,
  last_name = EXCLUDED.last_name,
  phone = EXCLUDED.phone,
  is_active = EXCLUDED.is_active,
  last_login_at = EXCLUDED.last_login_at,
  approval_status = EXCLUDED.approval_status,
  approved_at = EXCLUDED.approved_at;
