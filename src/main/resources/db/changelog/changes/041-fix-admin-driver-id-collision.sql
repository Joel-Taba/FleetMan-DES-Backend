--liquibase formatted sql
--changeset fleet-users:fix-admin-driver-id-collision-v1 splitStatements:true runOnChange:true

-- BUG : la migration 039-seed-platform-admins.sql réutilisait par erreur les IDs
-- a1000001..a1000004, déjà utilisés par les chauffeurs démo driver2..driver5
-- (voir db/demo-seed-kernel-fleet-ext.sql). Son "ON CONFLICT (id) DO UPDATE" a
-- donc écrasé username/first_name/last_name de 4 vrais comptes chauffeurs avec
-- des données d'administrateur (ex. driver2@fleetman.cm affichait "Sophie Kamga"
-- / "admin.kamga" au lieu de "Paul Kouam" / "driver.kouam").
--
-- 1) On restaure l'identité correcte de ces 4 chauffeurs.
UPDATE fleet.users SET username = 'driver.kouam', first_name = 'Paul', last_name = 'Kouam'
WHERE id = 'a1000001-0000-4000-8000-000000000001' AND email = 'driver2@fleetman.cm';

UPDATE fleet.users SET username = 'driver.nana', first_name = 'Amina', last_name = 'Nana'
WHERE id = 'a1000002-0000-4000-8000-000000000002' AND email = 'driver3@fleetman.cm';

UPDATE fleet.users SET username = 'driver.fouda', first_name = 'Eric', last_name = 'Fouda'
WHERE id = 'a1000003-0000-4000-8000-000000000003' AND email = 'driver4@fleetman.cm';

UPDATE fleet.users SET username = 'driver.bella', first_name = 'Claire', last_name = 'Bella'
WHERE id = 'a1000004-0000-4000-8000-000000000004' AND email = 'driver5@fleetman.cm';

-- 2) On (re)crée les 4 administrateurs de démo manquants avec des IDs qui
--    n'entrent en collision avec AUCUNE autre plage utilisée par les seeds
--    (préfixe 'ad' non utilisé ailleurs — vérifié sur tous les fichiers de seed).
INSERT INTO fleet.users (id, username, email, first_name, last_name, phone, is_active, last_login_at, approval_status, approved_at)
VALUES
  ('ad000001-0000-4000-8000-000000000001', 'admin.kamga',  'admin2@fleetman.cm', 'Sophie', 'Kamga',  '+237690000011', true,  NOW() - INTERVAL '1 day',   'APPROVED', NOW() - INTERVAL '60 days'),
  ('ad000002-0000-4000-8000-000000000002', 'admin.ndongo', 'admin3@fleetman.cm', 'Patrick','Ndongo', '+237690000012', true,  NOW() - INTERVAL '3 days',  'APPROVED', NOW() - INTERVAL '45 days'),
  ('ad000003-0000-4000-8000-000000000003', 'admin.moussa', 'admin4@fleetman.cm', 'Aïcha',  'Moussa', '+237690000013', false, NOW() - INTERVAL '15 days', 'APPROVED', NOW() - INTERVAL '30 days'),
  ('ad000004-0000-4000-8000-000000000004', 'admin.fotso',  'admin5@fleetman.cm', 'Eric',   'Fotso',  '+237690000014', true,  NOW() - INTERVAL '5 days',  'APPROVED', NOW() - INTERVAL '20 days')
ON CONFLICT (id) DO UPDATE SET
  username = EXCLUDED.username,
  email = EXCLUDED.email,
  first_name = EXCLUDED.first_name,
  last_name = EXCLUDED.last_name,
  phone = EXCLUDED.phone,
  is_active = EXCLUDED.is_active,
  approval_status = EXCLUDED.approval_status;
