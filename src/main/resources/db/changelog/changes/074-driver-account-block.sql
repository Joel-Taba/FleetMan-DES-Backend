--liquibase formatted sql

-- Blocage du compte d'un chauffeur par son gestionnaire : connexion refusée (users.is_active),
-- chauffeur non engageable, historique conservé ; le déblocage rétablit l'accès.

--changeset fleetman:074-driver-account-block
ALTER TABLE fleet.drivers ADD COLUMN IF NOT EXISTS account_blocked_at TIMESTAMP;
ALTER TABLE fleet.drivers ADD COLUMN IF NOT EXISTS account_blocked_reason VARCHAR(500);
ALTER TABLE fleet.drivers ADD COLUMN IF NOT EXISTS account_blocked_by UUID;
