--liquibase formatted sql

-- Suivi des budgets : journal de chaque mouvement d'un budget (création, changement de montant,
-- suppression, franchissement des seuils 80 % et 100 %, retour sous le seuil), avec son auteur.
-- Le journal survit à la suppression du budget (cible et mois recopiés). Les dépenses, elles,
-- viennent de fleet.expenses : le grand livre combine les deux.

--changeset fleetman:061-budget-events
CREATE TABLE fleet.budget_events (
    id            UUID PRIMARY KEY,
    budget_id     UUID REFERENCES fleet.budgets(id) ON DELETE SET NULL,
    scope         VARCHAR(10) NOT NULL,
    entity_id     UUID        NOT NULL,
    budget_month  DATE        NOT NULL,
    event_type    VARCHAR(20) NOT NULL
        CHECK (event_type IN ('CREATED', 'AMOUNT_CHANGED', 'DELETED', 'THRESHOLD_80', 'THRESHOLD_100', 'RECOVERED')),
    old_amount    NUMERIC(14,2),
    new_amount    NUMERIC(14,2),
    consumed      NUMERIC(14,2),
    actor_id      UUID REFERENCES fleet.users(id) ON DELETE SET NULL,
    note          TEXT,
    created_at    TIMESTAMP   NOT NULL,
    tenant_id     UUID
);
CREATE INDEX idx_budget_events_budget ON fleet.budget_events (budget_id, created_at);
CREATE INDEX idx_budget_events_entity ON fleet.budget_events (scope, entity_id, budget_month);

-- Budgets existants : leur création est reconstituée (le montant d'origine n'était pas conservé).
INSERT INTO fleet.budget_events (id, budget_id, scope, entity_id, budget_month, event_type, new_amount, consumed,
                                 actor_id, note, created_at, tenant_id)
SELECT gen_random_uuid(), b.id, b.scope, b.entity_id, b.budget_month, 'CREATED', b.amount, 0, b.created_by,
       'Reconstitué à la mise en place du suivi : montant actuel du budget', b.created_at, b.tenant_id
FROM fleet.budgets b;
