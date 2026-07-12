--liquibase formatted sql
--changeset yowyob:add-org-fields-v1 splitStatements:true

ALTER TABLE fleet.fleet_managers ADD COLUMN IF NOT EXISTS phone VARCHAR(50);
ALTER TABLE fleet.fleet_managers ADD COLUMN IF NOT EXISTS address VARCHAR(255);
ALTER TABLE fleet.fleet_managers ADD COLUMN IF NOT EXISTS city VARCHAR(100);
ALTER TABLE fleet.fleet_managers ADD COLUMN IF NOT EXISTS logo_url VARCHAR(255);

-- Ensure Ewane (Admin) also has a row in fleet_managers so she belongs to 'AXE CAPITAL'
INSERT INTO fleet.fleet_managers (user_id, company_name, subscription_status)
VALUES ('a0000000-0000-4000-8000-000000000101', 'AXE CAPITAL', 'PENDING')
ON CONFLICT (user_id) DO NOTHING;

-- Populate details for AXE CAPITAL managers/admins
UPDATE fleet.fleet_managers
SET phone = '+237699999999', address = 'Axe Capital HQ, Boulevard de la Liberté', city = 'Douala', logo_url = 'https://i.pravatar.cc/150?u=axe'
WHERE company_name = 'AXE CAPITAL';
