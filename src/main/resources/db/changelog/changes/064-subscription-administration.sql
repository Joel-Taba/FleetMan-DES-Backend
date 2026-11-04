--liquibase formatted sql

-- Pilotage des abonnements par le super-administrateur :
--   * fleet.subscription_events : journal de chaque entreprise (approbation, paiement, changement de
--     plan, prolongation, suspension, réactivation, relance, expiration) ;
--   * companies.suspension_reason / suspended_at : motif et date de la suspension ;
--   * subscription_orders : règlement enregistré à la main (mode, auteur, note).
-- Le journal démarre avec les paiements déjà enregistrés.

--changeset fleetman:064-subscription-events
CREATE TABLE IF NOT EXISTS fleet.subscription_events (
    id           UUID PRIMARY KEY,
    company_id   UUID NOT NULL REFERENCES fleet.companies(id) ON DELETE CASCADE,
    event_type   VARCHAR(30) NOT NULL,
    actor_id     UUID,
    plan_name    VARCHAR(150),
    period_end   DATE,
    amount       NUMERIC(14,2),
    reference    VARCHAR(120),
    reason       TEXT,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_subscription_events_company ON fleet.subscription_events (company_id, created_at DESC);

--changeset fleetman:064-company-suspension
ALTER TABLE fleet.companies
    ADD COLUMN IF NOT EXISTS suspension_reason TEXT,
    ADD COLUMN IF NOT EXISTS suspended_at      TIMESTAMP;

--changeset fleetman:064-order-manual-payment
ALTER TABLE fleet.subscription_orders
    ADD COLUMN IF NOT EXISTS payment_method VARCHAR(20),
    ADD COLUMN IF NOT EXISTS recorded_by    UUID,
    ADD COLUMN IF NOT EXISTS payment_note   TEXT;

--changeset fleetman:064-backfill-events
INSERT INTO fleet.subscription_events (id, company_id, event_type, plan_name, period_end, amount, reference, created_at)
SELECT gen_random_uuid(), o.company_id, 'PAYMENT_CONFIRMED', o.plan_name, o.period_end, o.amount, o.payment_reference, COALESCE(o.paid_at, o.updated_at)
FROM fleet.subscription_orders o
WHERE o.status = 'PAID' AND o.company_id IS NOT NULL AND o.amount > 0;
