--liquibase formatted sql
--changeset fleet-subscription:subscription-plans-v1 splitStatements:true

-- 1. Table des plans tarifaires
CREATE TABLE IF NOT EXISTS fleet.subscription_plans (
    id              UUID        PRIMARY KEY,
    name            VARCHAR(100) NOT NULL UNIQUE,
    description     TEXT,
    max_fleets      INT         NOT NULL DEFAULT 1,
    max_vehicles    INT         NOT NULL DEFAULT 5,
    max_drivers     INT         NOT NULL DEFAULT 10,
    monthly_price   NUMERIC(12,2) NOT NULL DEFAULT 0,
    annual_price    NUMERIC(12,2) NOT NULL DEFAULT 0,
    currency        VARCHAR(10) NOT NULL DEFAULT 'XAF',
    features        TEXT,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT now()
);

-- 2. Plans par défaut
INSERT INTO fleet.subscription_plans (id, name, description, max_fleets, max_vehicles, max_drivers, monthly_price, annual_price, features) VALUES
  ('55000001-0000-4000-8000-000000000001', 'Starter',    'Idéal pour les petites structures',      1,  5,  10,  25000,  250000, 'Gestion basique,Trajets,Documents'),
  ('55000002-0000-4000-8000-000000000002', 'Pro',        'Pour les PME en croissance',              3, 20,  50,  75000,  750000, 'Tout Starter,KPIs,Géofencing,Alertes,Scoring'),
  ('55000003-0000-4000-8000-000000000003', 'Enterprise', 'Solution complète sans limite',          10, 100, 500, 150000, 1500000, 'Tout Pro,API,Support dédié,SLA')
ON CONFLICT (id) DO NOTHING;

-- 3. Lien plan sur fleet_managers
ALTER TABLE fleet.fleet_managers
    ADD COLUMN IF NOT EXISTS plan_id UUID REFERENCES fleet.subscription_plans(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS subscription_status VARCHAR(20) DEFAULT 'PENDING'
        CHECK (subscription_status IN ('PENDING','ACTIVE','SUSPENDED','EXPIRED'));

-- Index
CREATE INDEX IF NOT EXISTS idx_plans_active ON fleet.subscription_plans(is_active);
CREATE INDEX IF NOT EXISTS idx_managers_plan ON fleet.fleet_managers(plan_id);

-- 4. Statut d'approbation sur les utilisateurs
ALTER TABLE fleet.users
    ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20) DEFAULT 'APPROVED'
        CHECK (approval_status IN ('PENDING','APPROVED','REJECTED')),
    ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS approved_by UUID,
    ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;

-- Index
CREATE INDEX IF NOT EXISTS idx_users_approval ON fleet.users(approval_status) WHERE approval_status = 'PENDING';
