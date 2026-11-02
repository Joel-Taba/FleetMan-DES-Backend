--liquibase formatted sql

-- Budget et dépenses :
--   * enveloppe mensuelle de flotte fixée par l'administrateur : fleets.monthly_budget devient
--     l'enveloppe récurrente qui crée le budget FLEET de chaque mois (une seule source) ;
--   * validation à deux niveaux : seuil d'approbation par entreprise, niveau requis et auteur
--     de chaque dépense, personne ne valide sa propre dépense ; validation possible par l'admin ;
--   * justificatif (photo / PDF) exigé pour approuver une dépense manuelle ;
--   * rattrapage : les opérations enregistrées avant la génération automatique des dépenses
--     (pleins, maintenances, incidents chiffrés) reçoivent leur dépense ; les montants consommés
--     des budgets, devenus faux (ex. 255 000 affichés pour 2 500 dépensés), sont recalculés.

--changeset fleetman:057-expense-policy
ALTER TABLE fleet.companies
    ADD COLUMN IF NOT EXISTS expense_approval_threshold NUMERIC(14,2) NOT NULL DEFAULT 100000
        CHECK (expense_approval_threshold >= 0);

--changeset fleetman:057-expense-approval
ALTER TABLE fleet.expenses
    ADD COLUMN IF NOT EXISTS created_by     UUID REFERENCES fleet.users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS approval_level VARCHAR(10) NOT NULL DEFAULT 'MANAGER'
        CHECK (approval_level IN ('MANAGER', 'ADMIN')),
    ADD COLUMN IF NOT EXISTS over_budget    BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS receipt_url    TEXT,
    ADD COLUMN IF NOT EXISTS receipt_name   VARCHAR(255),
    ADD COLUMN IF NOT EXISTS receipt_mime   VARCHAR(120);

-- L'administrateur peut désormais valider : le validateur est un utilisateur, pas forcément un gestionnaire.
ALTER TABLE fleet.expenses DROP CONSTRAINT IF EXISTS expenses_validated_by_fkey;
ALTER TABLE fleet.expenses
    ADD CONSTRAINT expenses_validated_by_fkey FOREIGN KEY (validated_by) REFERENCES fleet.users(id) ON DELETE SET NULL;

-- Nom du chauffeur : un identifiant technique y était enregistré au lieu du nom.
UPDATE fleet.expenses e
SET driver_full_name = NULLIF(TRIM(COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, '')), '')
FROM fleet.users u
WHERE u.id = e.driver_id AND (e.driver_full_name IS NULL OR e.driver_full_name ~ '^[0-9a-f]{8}-');

--changeset fleetman:057-budget-author
ALTER TABLE fleet.budgets ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES fleet.users(id) ON DELETE SET NULL;

-- Enveloppe récurrente → budget FLEET du mois en cours quand il n'existe pas.
INSERT INTO fleet.budgets (id, scope, entity_id, manager_id, budget_month, amount, consumed, alert_level, notes, tenant_id)
SELECT gen_random_uuid(), 'FLEET', f.id, f.manager_id, date_trunc('month', CURRENT_DATE)::date, f.monthly_budget, 0, 'NORMAL',
       'Enveloppe mensuelle de la flotte', f.tenant_id
FROM fleet.fleets f
WHERE f.deleted_at IS NULL AND f.monthly_budget > 0 AND f.manager_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM fleet.budgets b WHERE b.scope = 'FLEET' AND b.entity_id = f.id
                  AND b.budget_month = date_trunc('month', CURRENT_DATE)::date);

--changeset fleetman:057-backfill-auto-expenses
INSERT INTO fleet.expenses (id, expense_type, amount, description, expense_date, status, source_type, source_id,
                            validated_at, vehicle_id, vehicle_registration, fleet_id, manager_id, driver_id, tenant_id)
SELECT gen_random_uuid(), 'FUEL', r.price, 'Plein de carburant', COALESCE(r.recharge_date_time, NOW()),
       'APPROVED', 'AUTO', r.id, NOW(), v.id, v.license_plate, v.fleet_id, f.manager_id, r.driver_id, v.tenant_id
FROM fleet.fuel_recharges r
JOIN fleet.vehicles v ON v.id = r.vehicle_id
JOIN fleet.fleets f ON f.id = v.fleet_id
WHERE r.price > 0 AND f.manager_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM fleet.expenses e WHERE e.source_id = r.id);

INSERT INTO fleet.expenses (id, expense_type, amount, description, expense_date, status, source_type, source_id,
                            validated_at, vehicle_id, vehicle_registration, fleet_id, manager_id, tenant_id)
SELECT gen_random_uuid(), 'MAINTENANCE', m.cost, 'Maintenance', COALESCE(m.date_time, NOW()),
       'APPROVED', 'AUTO', m.id, NOW(), v.id, v.license_plate, v.fleet_id, f.manager_id, v.tenant_id
FROM fleet.maintenances m
JOIN fleet.vehicles v ON v.id = m.vehicle_id
JOIN fleet.fleets f ON f.id = v.fleet_id
WHERE m.cost > 0 AND f.manager_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM fleet.expenses e WHERE e.source_id = m.id);

INSERT INTO fleet.expenses (id, expense_type, amount, description, expense_date, status, source_type, source_id,
                            validated_at, vehicle_id, vehicle_registration, fleet_id, manager_id, tenant_id)
SELECT gen_random_uuid(), 'INCIDENT', i.cost, 'Incident', COALESCE(i.incident_date_time, NOW()),
       'APPROVED', 'AUTO', i.id, NOW(), v.id, v.license_plate, v.fleet_id, f.manager_id, v.tenant_id
FROM fleet.incidents i
JOIN fleet.vehicles v ON v.id = i.vehicle_id
JOIN fleet.fleets f ON f.id = v.fleet_id
WHERE i.cost > 0 AND f.manager_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM fleet.expenses e WHERE e.source_id = i.id);

--changeset fleetman:057-recompute-budgets
UPDATE fleet.budgets b SET consumed = COALESCE((
        SELECT SUM(e.amount) FROM fleet.expenses e
        WHERE e.status = 'APPROVED'
          AND e.expense_date >= b.budget_month AND e.expense_date < (b.budget_month + INTERVAL '1 month')
          AND ((b.scope = 'FLEET' AND e.fleet_id = b.entity_id) OR (b.scope = 'VEHICLE' AND e.vehicle_id = b.entity_id))
    ), 0);
UPDATE fleet.budgets SET alert_level = CASE
        WHEN consumed >= amount THEN 'EXCEEDED'
        WHEN consumed >= amount * 0.8 THEN 'WARNING'
        ELSE 'NORMAL' END;
