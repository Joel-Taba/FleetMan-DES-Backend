--liquibase formatted sql
--changeset fleet-subscription:subscription-features-v1 splitStatements:true

-- Période d'abonnement sur les gestionnaires
ALTER TABLE fleet.fleet_managers
    ADD COLUMN IF NOT EXISTS subscription_start DATE,
    ADD COLUMN IF NOT EXISTS subscription_end   DATE;

-- Grâce post-expiration (jours) au niveau plan
ALTER TABLE fleet.subscription_plans
    ADD COLUMN IF NOT EXISTS grace_days INT NOT NULL DEFAULT 7;

-- Fonctionnalités structurées par plan
CREATE TABLE IF NOT EXISTS fleet.plan_features (
    id             UUID PRIMARY KEY,
    plan_id        UUID NOT NULL REFERENCES fleet.subscription_plans(id) ON DELETE CASCADE,
    feature_key    VARCHAR(80) NOT NULL,
    feature_label  VARCHAR(150),
    enabled        BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_plan_feature UNIQUE (plan_id, feature_key)
);

CREATE INDEX IF NOT EXISTS idx_plan_features_plan
    ON fleet.plan_features(plan_id, enabled);

-- Seed fonctionnalités Starter
INSERT INTO fleet.plan_features (id, plan_id, feature_key, feature_label, enabled) VALUES
  ('66000001-0000-4000-8000-000000000001', '55000001-0000-4000-8000-000000000001', 'TRIPS',        'Trajets',           TRUE),
  ('66000002-0000-4000-8000-000000000002', '55000001-0000-4000-8000-000000000001', 'DOCUMENTS',    'Documents',         TRUE),
  ('66000003-0000-4000-8000-000000000003', '55000001-0000-4000-8000-000000000001', 'SCHEDULES',    'Plannings',         TRUE),
  ('66000004-0000-4000-8000-000000000004', '55000001-0000-4000-8000-000000000001', 'ASSIGNMENTS',  'Affectations',      TRUE),
  ('66000005-0000-4000-8000-000000000005', '55000001-0000-4000-8000-000000000001', 'OPERATIONS',   'Opérations terrain',TRUE)
ON CONFLICT (plan_id, feature_key) DO NOTHING;

-- Seed Pro (+ KPI, géofencing, alertes, scoring)
INSERT INTO fleet.plan_features (id, plan_id, feature_key, feature_label, enabled) VALUES
  ('66000011-0000-4000-8000-000000000011', '55000002-0000-4000-8000-000000000002', 'TRIPS',        'Trajets',           TRUE),
  ('66000012-0000-4000-8000-000000000012', '55000002-0000-4000-8000-000000000002', 'DOCUMENTS',    'Documents',         TRUE),
  ('66000013-0000-4000-8000-000000000013', '55000002-0000-4000-8000-000000000002', 'SCHEDULES',    'Plannings',         TRUE),
  ('66000014-0000-4000-8000-000000000014', '55000002-0000-4000-8000-000000000002', 'ASSIGNMENTS',  'Affectations',      TRUE),
  ('66000015-0000-4000-8000-000000000015', '55000002-0000-4000-8000-000000000002', 'OPERATIONS',   'Opérations terrain',TRUE),
  ('66000016-0000-4000-8000-000000000016', '55000002-0000-4000-8000-000000000002', 'KPI_REPORTS',  'KPI & Rapports',    TRUE),
  ('66000017-0000-4000-8000-000000000017', '55000002-0000-4000-8000-000000000002', 'GEOFENCING',   'Géofencing',        TRUE),
  ('66000018-0000-4000-8000-000000000018', '55000002-0000-4000-8000-000000000002', 'ALERTS',       'Alertes',           TRUE),
  ('66000019-0000-4000-8000-000000000019', '55000002-0000-4000-8000-000000000002', 'SCORING',      'Scoring conducteur',TRUE)
ON CONFLICT (plan_id, feature_key) DO NOTHING;

-- Seed Enterprise (tout activé)
INSERT INTO fleet.plan_features (id, plan_id, feature_key, feature_label, enabled) VALUES
  ('66000021-0000-4000-8000-000000000021', '55000003-0000-4000-8000-000000000003', 'TRIPS',        'Trajets',           TRUE),
  ('66000022-0000-4000-8000-000000000022', '55000003-0000-4000-8000-000000000003', 'DOCUMENTS',    'Documents',         TRUE),
  ('66000023-0000-4000-8000-000000000023', '55000003-0000-4000-8000-000000000003', 'SCHEDULES',    'Plannings',         TRUE),
  ('66000024-0000-4000-8000-000000000024', '55000003-0000-4000-8000-000000000003', 'ASSIGNMENTS',  'Affectations',      TRUE),
  ('66000025-0000-4000-8000-000000000025', '55000003-0000-4000-8000-000000000003', 'OPERATIONS',   'Opérations terrain',TRUE),
  ('66000026-0000-4000-8000-000000000026', '55000003-0000-4000-8000-000000000003', 'KPI_REPORTS',  'KPI & Rapports',    TRUE),
  ('66000027-0000-4000-8000-000000000027', '55000003-0000-4000-8000-000000000003', 'GEOFENCING',   'Géofencing',        TRUE),
  ('66000028-0000-4000-8000-000000000028', '55000003-0000-4000-8000-000000000003', 'ALERTS',       'Alertes',           TRUE),
  ('66000029-0000-4000-8000-000000000029', '55000003-0000-4000-8000-000000000003', 'SCORING',      'Scoring conducteur',TRUE),
  ('66000030-0000-4000-8000-000000000030', '55000003-0000-4000-8000-000000000003', 'API_ACCESS',   'Accès API',         TRUE)
ON CONFLICT (plan_id, feature_key) DO NOTHING;

-- Démo : manager actif sur plan Starter (1 an)
UPDATE fleet.fleet_managers fm
SET plan_id = '55000001-0000-4000-8000-000000000001',
    subscription_status = 'ACTIVE',
    subscription_start = CURRENT_DATE,
    subscription_end = CURRENT_DATE + INTERVAL '1 year'
WHERE fm.user_id IN (
    SELECT u.id FROM fleet.users u
    WHERE u.email = 'manager@fleetman.cm'
)
AND fm.plan_id IS NULL;
