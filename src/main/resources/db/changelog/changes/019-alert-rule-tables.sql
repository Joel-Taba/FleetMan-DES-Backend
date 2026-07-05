--liquibase formatted sql

--changeset alert-team:create-alert-rule-tables-v1 splitStatements:true
-- ============================================================
-- MODULE 8 — ALERTES & RÈGLES MÉTIER
-- Tables : alert_rules, alert_events
-- Schema : fleet
-- Dépendances : fleet.fleet_managers
-- ============================================================


-- ─────────────────────────────────────────────────────────────
-- 1. TABLE fleet.alert_rules — Règles d'alerte métier
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS fleet.alert_rules (
    id               UUID PRIMARY KEY,

    name             VARCHAR(255) NOT NULL,
    description      TEXT,

    manager_id       UUID NOT NULL REFERENCES fleet.fleet_managers(user_id) ON DELETE CASCADE,

    -- Type de déclencheur
    trigger_type     VARCHAR(30) NOT NULL
                     CHECK (trigger_type IN (
                         'DOCUMENT_EXPIRY','BUDGET_THRESHOLD','MAINTENANCE_ALERT_DUE',
                         'DRIVER_SCORE_DROP','FUEL_ANOMALY','INCIDENT_REPORTED','TRIP_OVERDUE'
                     )),

    -- Canal d'action
    action_type      VARCHAR(25) NOT NULL
                     CHECK (action_type IN ('IN_APP_NOTIFICATION','EMAIL')),

    -- Destinataire
    target_role      VARCHAR(15) NOT NULL
                     CHECK (target_role IN ('MANAGER','ADMIN','DRIVER')),

    active           BOOLEAN NOT NULL DEFAULT TRUE,

    -- TRUE = règle système provisionnée par défaut (non supprimable)
    system_template  BOOLEAN NOT NULL DEFAULT FALSE,

    -- Valeur seuil encodée en String (ex: "30" pour 30 jours, "80" pour 80%)
    condition_value  VARCHAR(100),

    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_alert_rules_manager
    ON fleet.alert_rules(manager_id, active);

CREATE INDEX IF NOT EXISTS idx_alert_rules_trigger
    ON fleet.alert_rules(manager_id, trigger_type, active);

CREATE OR REPLACE TRIGGER trg_alert_rules_updated_at
    BEFORE UPDATE ON fleet.alert_rules
    FOR EACH ROW EXECUTE FUNCTION fleet.update_updated_at_column();


-- ─────────────────────────────────────────────────────────────
-- 2. TABLE fleet.alert_events — Événements d'alerte (notifications in-app)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS fleet.alert_events (
    id                   UUID PRIMARY KEY,

    -- Règle qui a déclenché cet événement (NULL si déclenché manuellement)
    rule_id              UUID REFERENCES fleet.alert_rules(id) ON DELETE SET NULL,
    rule_name            VARCHAR(255),

    -- Manager destinataire
    manager_id           UUID NOT NULL REFERENCES fleet.fleet_managers(user_id) ON DELETE CASCADE,

    trigger_type         VARCHAR(30),
    action_type          VARCHAR(25),

    -- Contenu de la notification
    title                VARCHAR(255) NOT NULL,
    message              TEXT,

    -- Entité source qui a déclenché la règle
    source_entity_id     UUID,
    source_entity_type   VARCHAR(50),

    -- Statut de lecture
    read_status          VARCHAR(15) NOT NULL DEFAULT 'UNREAD'
                         CHECK (read_status IN ('UNREAD','READ','DISMISSED')),

    sent_at              TIMESTAMP NOT NULL DEFAULT now(),
    read_at              TIMESTAMP
);

-- Index pour le polling des notifications non lues (endpoint header badge)
CREATE INDEX IF NOT EXISTS idx_alert_events_unread
    ON fleet.alert_events(manager_id, sent_at DESC)
    WHERE read_status = 'UNREAD';

-- Index pour l'historique complet
CREATE INDEX IF NOT EXISTS idx_alert_events_manager
    ON fleet.alert_events(manager_id, sent_at DESC);
