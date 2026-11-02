--liquibase formatted sql
--changeset fleet-business:subscription-catalog-v1 splitStatements:true

-- Plans tarifaires cohérents : 4 paliers cumulatifs, facturation mensuelle ou annuelle,
-- commandes d'abonnement prêtes pour le futur service de paiement.
-- Convention des limites : 0 = illimité (déjà appliquée par PlanLimitGuard).

-- 1. Ordre d'affichage et plan mis en avant
ALTER TABLE fleet.subscription_plans ADD COLUMN IF NOT EXISTS sort_order INTEGER NOT NULL DEFAULT 0;
ALTER TABLE fleet.subscription_plans ADD COLUMN IF NOT EXISTS recommended BOOLEAN NOT NULL DEFAULT false;

-- 2. Cycle de facturation de l'abonnement en cours / demandé à l'inscription
ALTER TABLE fleet.fleet_managers ADD COLUMN IF NOT EXISTS billing_cycle VARCHAR(10)
    CHECK (billing_cycle IN ('MONTHLY', 'ANNUAL'));
ALTER TABLE fleet.fleet_managers ADD COLUMN IF NOT EXISTS requested_billing_cycle VARCHAR(10)
    CHECK (requested_billing_cycle IN ('MONTHLY', 'ANNUAL'));

-- 3. Catalogue de référence (modifiable ensuite depuis l'administration)
UPDATE fleet.subscription_plans SET
    name = 'Découverte', description = 'Pour démarrer gratuitement avec une petite flotte',
    max_fleets = 1, max_vehicles = 3, max_drivers = 5,
    monthly_price = 0, annual_price = NULL, currency = 'XAF',
    is_active = true, sort_order = 1, recommended = false, updated_at = now()
WHERE id = '55000001-0000-4000-8000-000000000001';

INSERT INTO fleet.subscription_plans (id, name, description, max_fleets, max_vehicles, max_drivers,
                                      monthly_price, annual_price, currency, is_active, created_at,
                                      updated_at, grace_days, sort_order, recommended, tenant_id)
SELECT '55000004-0000-4000-8000-000000000004', 'Essentiel',
       'Pour les petites entreprises qui suivent leurs opérations terrain',
       2, 15, 30, 15000, 150000, 'XAF', true, now(), now(), 7, 2, false,
       (SELECT tenant_id FROM fleet.subscription_plans WHERE id = '55000001-0000-4000-8000-000000000001')
WHERE NOT EXISTS (SELECT 1 FROM fleet.subscription_plans WHERE id = '55000004-0000-4000-8000-000000000004');

UPDATE fleet.subscription_plans SET
    name = 'Pro', description = 'Pour les PME qui pilotent leur flotte par les indicateurs',
    max_fleets = 5, max_vehicles = 50, max_drivers = 100,
    monthly_price = 45000, annual_price = 450000, currency = 'XAF',
    is_active = true, sort_order = 3, recommended = true, updated_at = now()
WHERE id = '55000002-0000-4000-8000-000000000002';

UPDATE fleet.subscription_plans SET
    name = 'Entreprise', description = 'Pour les grandes flottes, sans limite et avec accès API',
    max_fleets = 0, max_vehicles = 0, max_drivers = 0,
    monthly_price = 120000, annual_price = 1200000, currency = 'XAF',
    is_active = true, sort_order = 4, recommended = false, updated_at = now()
WHERE id = '55000003-0000-4000-8000-000000000003';

-- Fonctionnalités cumulatives : chaque palier inclut tout le palier précédent.
DELETE FROM fleet.plan_features WHERE plan_id IN (
    '55000001-0000-4000-8000-000000000001', '55000002-0000-4000-8000-000000000002',
    '55000003-0000-4000-8000-000000000003', '55000004-0000-4000-8000-000000000004');

INSERT INTO fleet.plan_features (id, plan_id, feature_key, feature_label, enabled)
SELECT gen_random_uuid(), p.plan_id, f.key, f.label, f.tier <= p.tier
FROM (VALUES ('55000001-0000-4000-8000-000000000001'::uuid, 1),
             ('55000004-0000-4000-8000-000000000004'::uuid, 2),
             ('55000002-0000-4000-8000-000000000002'::uuid, 3),
             ('55000003-0000-4000-8000-000000000003'::uuid, 4)) AS p(plan_id, tier)
CROSS JOIN (VALUES ('TRIPS', 'Trajets', 1), ('DOCUMENTS', 'Documents', 1),
                   ('SCHEDULES', 'Plannings', 1), ('ASSIGNMENTS', 'Affectations', 1),
                   ('OPERATIONS', 'Opérations terrain', 2),
                   ('KPI_REPORTS', 'KPI & Rapports', 3), ('GEOFENCING', 'Géofencing', 3),
                   ('ALERTS', 'Alertes', 3), ('SCORING', 'Scoring conducteur', 3),
                   ('API_ACCESS', 'Accès API', 4)) AS f(key, label, tier);

-- Libellé marketing aligné sur les fonctionnalités réellement activées.
UPDATE fleet.subscription_plans sp
SET features = (SELECT string_agg(pf.feature_label, ', ' ORDER BY pf.feature_label)
                FROM fleet.plan_features pf WHERE pf.plan_id = sp.id AND pf.enabled)
WHERE sp.id IN ('55000001-0000-4000-8000-000000000001', '55000002-0000-4000-8000-000000000002',
                '55000003-0000-4000-8000-000000000003', '55000004-0000-4000-8000-000000000004');

-- 4. Abonnés des plans de test et de l'ancien Starter : plus petit plan du catalogue
--    couvrant leur usage réel (flottes, véhicules, chauffeurs).
WITH usage AS (
    SELECT fm.user_id,
           (SELECT COUNT(*) FROM fleet.fleets f WHERE f.manager_id = fm.user_id AND f.deleted_at IS NULL) AS fleets,
           (SELECT COUNT(*) FROM fleet.vehicles v WHERE v.manager_id = fm.user_id AND v.deleted_at IS NULL) AS vehicles,
           (SELECT COUNT(*) FROM fleet.drivers d JOIN fleet.fleets f ON f.id = d.fleet_id
             WHERE f.manager_id = fm.user_id AND d.deleted_at IS NULL) AS drivers
    FROM fleet.fleet_managers fm
    WHERE fm.plan_id IS NOT NULL
      AND fm.plan_id NOT IN ('55000002-0000-4000-8000-000000000002', '55000003-0000-4000-8000-000000000003',
                             '55000004-0000-4000-8000-000000000004')
),
target AS (
    SELECT u.user_id,
           (SELECT sp.id FROM fleet.subscription_plans sp
             WHERE sp.id IN ('55000001-0000-4000-8000-000000000001', '55000004-0000-4000-8000-000000000004',
                             '55000002-0000-4000-8000-000000000002', '55000003-0000-4000-8000-000000000003')
               AND (sp.max_fleets = 0 OR sp.max_fleets >= u.fleets)
               AND (sp.max_vehicles = 0 OR sp.max_vehicles >= u.vehicles)
               AND (sp.max_drivers = 0 OR sp.max_drivers >= u.drivers)
             ORDER BY sp.sort_order LIMIT 1) AS plan_id
    FROM usage u
)
UPDATE fleet.fleet_managers fm
SET plan_id = t.plan_id
FROM target t
WHERE fm.user_id = t.user_id AND t.plan_id IS NOT NULL;

-- Plans de test : désactivés, conservés pour l'historique.
UPDATE fleet.subscription_plans SET is_active = false, updated_at = now()
WHERE id NOT IN ('55000001-0000-4000-8000-000000000001', '55000002-0000-4000-8000-000000000002',
                 '55000003-0000-4000-8000-000000000003', '55000004-0000-4000-8000-000000000004');

-- Cycle des abonnements existants : annuel pour un plan payant (ils duraient déjà 1 an),
-- aucun pour le plan gratuit, qui devient permanent (pas de date de fin).
UPDATE fleet.fleet_managers fm
SET billing_cycle = CASE WHEN sp.monthly_price > 0 THEN 'ANNUAL' END,
    subscription_end = CASE WHEN sp.monthly_price > 0 THEN fm.subscription_end END
FROM fleet.subscription_plans sp
WHERE sp.id = fm.plan_id AND fm.subscription_status IN ('ACTIVE', 'EXPIRED');

-- 5. Commandes d'abonnement : la souscription (devis) est séparée du règlement.
--    Le règlement est délégué à un port de paiement (simulateur tant que le service
--    de paiement n'est pas branché) ; une commande n'est payée qu'une seule fois.
CREATE TABLE IF NOT EXISTS fleet.subscription_orders (
    id                 UUID PRIMARY KEY,
    manager_id         UUID NOT NULL REFERENCES fleet.fleet_managers(user_id) ON DELETE CASCADE,
    plan_id            UUID NOT NULL REFERENCES fleet.subscription_plans(id),
    plan_name          VARCHAR(100) NOT NULL,
    billing_cycle      VARCHAR(10) CHECK (billing_cycle IN ('MONTHLY', 'ANNUAL')),
    amount             NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),
    currency           VARCHAR(10) NOT NULL DEFAULT 'XAF',
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                           CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'CANCELLED')),
    payment_provider   VARCHAR(50),
    payment_reference  VARCHAR(100),
    period_start       DATE,
    period_end         DATE,
    created_at         TIMESTAMP NOT NULL DEFAULT now(),
    paid_at            TIMESTAMP,
    updated_at         TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_subscription_orders_manager ON fleet.subscription_orders(manager_id, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uq_subscription_orders_payment_ref
    ON fleet.subscription_orders(payment_provider, payment_reference) WHERE payment_reference IS NOT NULL;
