--liquibase formatted sql

--changeset scoring-team:create-driver-score-tables-v1 splitStatements:true
-- ============================================================
-- MODULE 5 — SCORING CONDUCTEUR
-- Table : driver_scores
-- Schema : fleet
-- Dépendances : fleet.drivers, fleet.fleets, fleet.fleet_managers
-- ============================================================

CREATE TABLE IF NOT EXISTS fleet.driver_scores (
    id                          UUID PRIMARY KEY,
    driver_id                   UUID NOT NULL REFERENCES fleet.drivers(user_id) ON DELETE CASCADE,
    fleet_id                    UUID NOT NULL REFERENCES fleet.fleets(id) ON DELETE CASCADE,
    manager_id                  UUID NOT NULL REFERENCES fleet.fleet_managers(user_id) ON DELETE CASCADE,

    period_type                 VARCHAR(10) NOT NULL
                                CHECK (period_type IN ('WEEKLY','MONTHLY')),
    period_start                DATE NOT NULL,
    period_end                  DATE NOT NULL,

    -- Données brutes de la période
    incident_count              INT NOT NULL DEFAULT 0,
    total_trips                 INT NOT NULL DEFAULT 0,
    fuel_per_100km              NUMERIC(8,2),
    fleet_avg_fuel_per_100km    NUMERIC(8,2),
    doc_compliance_rate         NUMERIC(6,2),
    abnormal_maintenance_count  INT NOT NULL DEFAULT 0,
    completed_assignments       INT NOT NULL DEFAULT 0,
    no_show_assignments         INT NOT NULL DEFAULT 0,

    -- Scores composantes (0-100)
    incident_score              NUMERIC(6,1) NOT NULL DEFAULT 0,
    fuel_score                  NUMERIC(6,1) NOT NULL DEFAULT 0,
    compliance_score            NUMERIC(6,1) NOT NULL DEFAULT 0,
    punctuality_score           NUMERIC(6,1) NOT NULL DEFAULT 0,
    maintenance_score           NUMERIC(6,1) NOT NULL DEFAULT 0,

    -- Score final pondéré et badge
    final_score                 NUMERIC(6,1) NOT NULL DEFAULT 0,
    badge                       VARCHAR(20) NOT NULL DEFAULT 'INSUFFICIENT'
                                CHECK (badge IN ('EXCELLENCE','GOOD','SATISFACTORY','WARNING','INSUFFICIENT')),

    calculated_at               TIMESTAMP NOT NULL DEFAULT now(),

    -- Un seul score par chauffeur par période
    CONSTRAINT uq_score_driver_period UNIQUE (driver_id, period_type, period_start)
);

-- Index pour les requêtes fréquentes
CREATE INDEX IF NOT EXISTS idx_scores_driver_period
    ON fleet.driver_scores(driver_id, period_type, period_start DESC);

CREATE INDEX IF NOT EXISTS idx_scores_fleet_period
    ON fleet.driver_scores(fleet_id, period_type, period_start DESC);

-- Index pour les classements (TOP/BOTTOM)
CREATE INDEX IF NOT EXISTS idx_scores_fleet_ranking
    ON fleet.driver_scores(fleet_id, period_type, period_start, final_score DESC);

CREATE INDEX IF NOT EXISTS idx_scores_manager
    ON fleet.driver_scores(manager_id, period_type, period_start DESC);
