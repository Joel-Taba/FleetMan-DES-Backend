--liquibase formatted sql

-- Modèle « entreprise » : l'administrateur (FLEET_ADMIN) est l'entreprise cliente, les
-- gestionnaires (FLEET_MANAGER) sont ses employés.
--
--   * fleet.companies : une entreprise par administrateur (id = user_id de l'admin), avec son
--     nom, son logo et son abonnement (plan, statut, période, cycle, demande en attente).
--     Les limites du plan portent sur toute l'entreprise (flottes, véhicules, chauffeurs).
--   * Les entreprises existantes démarrent sur l'offre gratuite ; les anciens abonnements des
--     gestionnaires restent dans fleet_managers à titre d'archive et ne sont plus lus.
--   * Les inscriptions publiques en attente deviennent des demandes d'entreprise.
--   * fleets.company_id : une flotte appartient toujours à une entreprise.
--   * Gestionnaires : statut de compte ACTIVE / SUSPENDED (motif) / CLOSED (clôture, historique
--     conservé) au lieu d'un simple interrupteur actif / inactif.

--changeset fleetman:056-companies
CREATE TABLE IF NOT EXISTS fleet.companies (
    id                      UUID PRIMARY KEY REFERENCES fleet.platform_admins(user_id) ON DELETE CASCADE,
    name                    VARCHAR(150) NOT NULL,
    logo_url                VARCHAR(500),
    plan_id                 UUID REFERENCES fleet.subscription_plans(id) ON DELETE SET NULL,
    subscription_status     VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
        CHECK (subscription_status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'EXPIRED')),
    subscription_start      DATE,
    subscription_end        DATE,
    billing_cycle           VARCHAR(10) CHECK (billing_cycle IN ('MONTHLY', 'ANNUAL')),
    requested_plan_id       UUID REFERENCES fleet.subscription_plans(id) ON DELETE SET NULL,
    requested_billing_cycle VARCHAR(10) CHECK (requested_billing_cycle IN ('MONTHLY', 'ANNUAL')),
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    tenant_id               UUID
);

-- Entreprises existantes : nom de l'administrateur, offre gratuite permanente.
INSERT INTO fleet.companies (id, name, plan_id, subscription_status, subscription_start, tenant_id)
SELECT pa.user_id,
       COALESCE(NULLIF(TRIM(COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, '')), ''), u.username, 'Entreprise'),
       (SELECT id FROM fleet.subscription_plans
        WHERE is_active AND monthly_price = 0 ORDER BY sort_order LIMIT 1),
       'ACTIVE', CURRENT_DATE, u.tenant_id
FROM fleet.platform_admins pa
JOIN fleet.users u ON u.id = pa.user_id
ON CONFLICT (id) DO NOTHING;

--changeset fleetman:056-pending-signups-become-companies
-- Inscriptions publiques en attente (compte non approuvé, plan demandé) : demandes d'entreprise.
INSERT INTO fleet.platform_admins (user_id)
SELECT fm.user_id FROM fleet.fleet_managers fm JOIN fleet.users u ON u.id = fm.user_id
WHERE u.approval_status = 'PENDING' AND u.deleted_at IS NULL AND fm.requested_plan_id IS NOT NULL
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO fleet.companies (id, name, subscription_status, requested_plan_id, requested_billing_cycle, tenant_id)
SELECT fm.user_id, COALESCE(NULLIF(TRIM(fm.company_name), ''), u.username), 'PENDING',
       fm.requested_plan_id, fm.requested_billing_cycle, u.tenant_id
FROM fleet.fleet_managers fm JOIN fleet.users u ON u.id = fm.user_id
WHERE u.approval_status = 'PENDING' AND u.deleted_at IS NULL AND fm.requested_plan_id IS NOT NULL
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, subscription_status = 'PENDING', plan_id = NULL,
    subscription_start = NULL, requested_plan_id = EXCLUDED.requested_plan_id,
    requested_billing_cycle = EXCLUDED.requested_billing_cycle;

-- Leur ancien profil gestionnaire disparaît s'il n'a aucune flotte (sinon il reste pour l'historique).
DELETE FROM fleet.fleet_managers fm USING fleet.users u
WHERE u.id = fm.user_id AND u.approval_status = 'PENDING' AND fm.requested_plan_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM fleet.fleets f WHERE f.manager_id = fm.user_id);

--changeset fleetman:056-fleet-company
ALTER TABLE fleet.fleets ADD COLUMN IF NOT EXISTS company_id UUID REFERENCES fleet.companies(id);
UPDATE fleet.fleets f SET company_id = fm.created_by_admin_id
FROM fleet.fleet_managers fm
WHERE f.manager_id = fm.user_id AND f.company_id IS NULL;
UPDATE fleet.fleets f SET company_id = f.created_by
WHERE f.company_id IS NULL AND EXISTS (SELECT 1 FROM fleet.companies c WHERE c.id = f.created_by);
CREATE INDEX IF NOT EXISTS idx_fleets_company ON fleet.fleets (company_id) WHERE deleted_at IS NULL;

--changeset fleetman:056-orders-company
ALTER TABLE fleet.subscription_orders ADD COLUMN IF NOT EXISTS company_id UUID REFERENCES fleet.companies(id) ON DELETE CASCADE;
ALTER TABLE fleet.subscription_orders ALTER COLUMN manager_id DROP NOT NULL;
UPDATE fleet.subscription_orders o SET company_id = fm.created_by_admin_id
FROM fleet.fleet_managers fm WHERE o.manager_id = fm.user_id AND o.company_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_subscription_orders_company ON fleet.subscription_orders (company_id, created_at DESC);

--changeset fleetman:056-manager-account-status
ALTER TABLE fleet.fleet_managers
    ADD COLUMN IF NOT EXISTS account_status    VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (account_status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    ADD COLUMN IF NOT EXISTS suspension_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS status_changed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS closed_at         TIMESTAMP;

UPDATE fleet.fleet_managers fm SET account_status = 'CLOSED', closed_at = COALESCE(u.deleted_at, NOW()), status_changed_at = NOW()
FROM fleet.users u WHERE u.id = fm.user_id AND u.deleted_at IS NOT NULL;
UPDATE fleet.fleet_managers fm SET account_status = 'SUSPENDED', status_changed_at = NOW(),
    suspension_reason = 'Compte désactivé avant la refonte des gestionnaires'
FROM fleet.users u
WHERE u.id = fm.user_id AND u.deleted_at IS NULL AND NOT u.is_active AND fm.account_status = 'ACTIVE';

COMMENT ON COLUMN fleet.fleet_managers.plan_id IS 'Archive (avant 056) : l''abonnement est porté par fleet.companies.';
COMMENT ON COLUMN fleet.fleet_managers.subscription_status IS 'Archive (avant 056) : l''abonnement est porté par fleet.companies.';
COMMENT ON COLUMN fleet.fleet_managers.company_name IS 'Archive (avant 056) : le nom de l''entreprise est fleet.companies.name.';
