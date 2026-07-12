--liquibase formatted sql
--changeset gabriel:seed-axe-capital-data splitStatements:true

INSERT INTO fleet.users (id, username, email, first_name, last_name, is_active, approval_status)
VALUES
  ('a0000000-0000-4000-8000-000000000101', 'NehemieAdmin', 'nehemie@gmail.com', 'Nehemie', 'Admin', true, 'APPROVED'),
  ('a0000000-0000-4000-8000-000000000102', 'TuringManager', 'turing@gmail.com', 'Alan', 'Turing', true, 'APPROVED')
ON CONFLICT (id) DO NOTHING;

INSERT INTO fleet.fleet_managers (user_id, company_name, subscription_status)
VALUES 
  ('a0000000-0000-4000-8000-000000000102', 'AXE CAPITAL', 'PENDING')
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO fleet.fleets (id, manager_id, name, phone_number)
VALUES
  ('b0000000-0000-4000-8000-000000000102', 'a0000000-0000-4000-8000-000000000102', 'AXE CAPITAL Fleet 1', '+237677000102')
ON CONFLICT (id) DO NOTHING;
