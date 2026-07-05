--liquibase formatted sql

--changeset budget-team:create-expense-budget-tables-v1 splitStatements:true
-- ============================================================
-- MODULE 4 — DÉPENSES & BUDGET
-- Tables : expenses, budgets
-- Schema : fleet
-- Dépendances : fleet.vehicles, fleet.fleets, fleet.fleet_managers
-- ============================================================


-- ─────────────────────────────────────────────────────────────
-- 1. TABLE fleet.expenses — Dépenses opérationnelles
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS fleet.expenses (
    id                   UUID PRIMARY KEY,

    expense_type         VARCHAR(30) NOT NULL
                         CHECK (expense_type IN ('FUEL','MAINTENANCE','INCIDENT','FINE','TOLL','OTHER')),

    amount               NUMERIC(14, 2) NOT NULL
                         CHECK (amount > 0),

    description          TEXT,

    expense_date         TIMESTAMP NOT NULL DEFAULT now(),

    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                         CHECK (status IN ('PENDING','APPROVED','REJECTED')),

    -- Source AUTO = générée depuis une opération existante
    -- Source MANUAL = saisie directe par Manager ou Driver
    source_type          VARCHAR(10) NOT NULL DEFAULT 'MANUAL'
                         CHECK (source_type IN ('AUTO','MANUAL')),

    -- Référence vers l'opération source (fuel_recharges.id, maintenances.id ou incidents.id)
    source_id            UUID,

    rejection_reason     TEXT,
    validated_at         TIMESTAMP,
    validated_by         UUID REFERENCES fleet.fleet_managers(user_id) ON DELETE SET NULL,

    vehicle_id           UUID NOT NULL REFERENCES fleet.vehicles(id) ON DELETE CASCADE,
    vehicle_registration VARCHAR(50),

    fleet_id             UUID NOT NULL REFERENCES fleet.fleets(id) ON DELETE CASCADE,

    manager_id           UUID NOT NULL REFERENCES fleet.fleet_managers(user_id) ON DELETE CASCADE,

    driver_id            UUID REFERENCES fleet.drivers(user_id) ON DELETE SET NULL,
    driver_full_name     VARCHAR(255),

    created_at           TIMESTAMP NOT NULL DEFAULT now()
);

-- Index pour les requêtes fréquentes
CREATE INDEX IF NOT EXISTS idx_expenses_manager_id
    ON fleet.expenses(manager_id, expense_date DESC);

CREATE INDEX IF NOT EXISTS idx_expenses_vehicle_id
    ON fleet.expenses(vehicle_id, expense_date DESC);

CREATE INDEX IF NOT EXISTS idx_expenses_fleet_id
    ON fleet.expenses(fleet_id, expense_date DESC);

CREATE INDEX IF NOT EXISTS idx_expenses_driver_id
    ON fleet.expenses(driver_id)
    WHERE driver_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_expenses_status
    ON fleet.expenses(status, manager_id);

CREATE INDEX IF NOT EXISTS idx_expenses_type
    ON fleet.expenses(expense_type, manager_id);

-- Index partiel pour les agrégats de budget (dépenses approuvées uniquement)
CREATE INDEX IF NOT EXISTS idx_expenses_approved_fleet_date
    ON fleet.expenses(fleet_id, expense_date)
    WHERE status = 'APPROVED';

CREATE INDEX IF NOT EXISTS idx_expenses_approved_vehicle_date
    ON fleet.expenses(vehicle_id, expense_date)
    WHERE status = 'APPROVED';


-- ─────────────────────────────────────────────────────────────
-- 2. TABLE fleet.budgets — Budgets mensuels
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS fleet.budgets (
    id              UUID PRIMARY KEY,

    -- FLEET = budget pour une flotte entière
    -- VEHICLE = budget pour un véhicule spécifique
    scope           VARCHAR(10) NOT NULL
                    CHECK (scope IN ('FLEET','VEHICLE')),

    -- ID de la flotte (scope=FLEET) ou du véhicule (scope=VEHICLE)
    entity_id       UUID NOT NULL,

    manager_id      UUID NOT NULL REFERENCES fleet.fleet_managers(user_id) ON DELETE CASCADE,

    -- Normalisé au 1er du mois (ex: 2026-06-01)
    budget_month    DATE NOT NULL,

    amount          NUMERIC(14, 2) NOT NULL
                    CHECK (amount > 0),

    -- Montant consommé — recalculé dynamiquement par le service
    consumed        NUMERIC(14, 2) NOT NULL DEFAULT 0
                    CHECK (consumed >= 0),

    -- Niveau d'alerte courant
    alert_level     VARCHAR(10) NOT NULL DEFAULT 'NORMAL'
                    CHECK (alert_level IN ('NORMAL','WARNING','EXCEEDED')),

    -- Flags pour éviter les alertes en double
    alert_80_sent   BOOLEAN NOT NULL DEFAULT FALSE,
    alert_100_sent  BOOLEAN NOT NULL DEFAULT FALSE,

    notes           TEXT,

    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now(),

    -- Contrainte : un seul budget par entité par mois
    CONSTRAINT uq_budget_entity_month UNIQUE (scope, entity_id, budget_month)
);

CREATE INDEX IF NOT EXISTS idx_budgets_manager_id
    ON fleet.budgets(manager_id, budget_month DESC);

CREATE INDEX IF NOT EXISTS idx_budgets_entity
    ON fleet.budgets(scope, entity_id, budget_month DESC);

-- Index pour le job d'alerte (budgets actifs du mois courant)
CREATE INDEX IF NOT EXISTS idx_budgets_active_month
    ON fleet.budgets(manager_id, budget_month)
    WHERE alert_level IN ('WARNING','EXCEEDED');

-- Trigger updated_at
CREATE OR REPLACE TRIGGER trg_budgets_updated_at
    BEFORE UPDATE ON fleet.budgets
    FOR EACH ROW EXECUTE FUNCTION fleet.update_updated_at_column();
