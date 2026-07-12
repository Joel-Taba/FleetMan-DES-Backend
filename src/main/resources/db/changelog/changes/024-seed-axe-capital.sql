--liquibase formatted sql
--changeset gabriel:seed-axe-capital-data-v3 splitStatements:true

-- 1. Nettoyer les anciennes données si elles existent pour éviter les violations de contraintes (comme users_email_key ou clés étrangères)
DELETE FROM fleet.fleets 
WHERE manager_id IN (
  SELECT user_id FROM fleet.fleet_managers WHERE user_id IN ('a0000000-0000-4000-8000-000000000101', 'a0000000-0000-4000-8000-000000000102')
) OR manager_id IN (
  SELECT id FROM fleet.users WHERE email IN ('nehemie@gmail.com', 'ewane@gmail.com', 'turing@gmail.com')
);

DELETE FROM fleet.fleet_managers 
WHERE user_id IN ('a0000000-0000-4000-8000-000000000101', 'a0000000-0000-4000-8000-000000000102') 
   OR user_id IN (SELECT id FROM fleet.users WHERE email IN ('nehemie@gmail.com', 'ewane@gmail.com', 'turing@gmail.com'));

DELETE FROM fleet.users 
WHERE id IN ('a0000000-0000-4000-8000-000000000101', 'a0000000-0000-4000-8000-000000000102') 
   OR email IN ('nehemie@gmail.com', 'ewane@gmail.com', 'turing@gmail.com');

-- 2. Insérer proprement les comptes de test et la flotte
INSERT INTO fleet.users (id, username, email, first_name, last_name, is_active, approval_status)
VALUES
  ('a0000000-0000-4000-8000-000000000101', 'NehemieAdmin', 'ewane@gmail.com', 'Nehemie', 'Admin', true, 'APPROVED'),
  ('a0000000-0000-4000-8000-000000000102', 'TuringManager', 'turing@gmail.com', 'Alan', 'Turing', true, 'APPROVED');

INSERT INTO fleet.fleet_managers (user_id, company_name, subscription_status)
VALUES 
  ('a0000000-0000-4000-8000-000000000102', 'AXE CAPITAL', 'PENDING');

INSERT INTO fleet.fleets (id, manager_id, name, phone_number)
VALUES
  ('b0000000-0000-4000-8000-000000000102', 'a0000000-0000-4000-8000-000000000102', 'AXE CAPITAL Fleet 1', '+237677000102');
