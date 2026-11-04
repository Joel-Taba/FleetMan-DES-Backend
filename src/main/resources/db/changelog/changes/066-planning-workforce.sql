--liquibase formatted sql

-- Plannings au plus près du terrain :
--   * règles de temps de travail propres à chaque entreprise (durée maximale d'un poste, repos
--     minimal entre deux postes, plafond hebdomadaire, jours consécutifs), appliquées en
--     avertissement : un dépassement exige un motif, conservé sur le poste ;
--   * modifications après publication tracées (motif, date) et accusé de réception du chauffeur ;
--   * modèles de postes (matin, après-midi, nuit…) par entreprise.

--changeset fleetman:066-company-work-rules
ALTER TABLE fleet.companies
    ADD COLUMN IF NOT EXISTS max_shift_hours      NUMERIC(4,1) NOT NULL DEFAULT 12,
    ADD COLUMN IF NOT EXISTS min_rest_hours       NUMERIC(4,1) NOT NULL DEFAULT 11,
    ADD COLUMN IF NOT EXISTS max_weekly_hours     NUMERIC(5,1) NOT NULL DEFAULT 60,
    ADD COLUMN IF NOT EXISTS max_consecutive_days INTEGER      NOT NULL DEFAULT 6;

--changeset fleetman:066-assignment-tracking
ALTER TABLE fleet.assignments
    ADD COLUMN IF NOT EXISTS acknowledged_at   TIMESTAMP,
    ADD COLUMN IF NOT EXISTS change_reason     TEXT,
    ADD COLUMN IF NOT EXISTS changed_at        TIMESTAMP,
    ADD COLUMN IF NOT EXISTS work_rule_override TEXT,
    ADD COLUMN IF NOT EXISTS cancel_reason     TEXT;

--changeset fleetman:066-shift-templates
CREATE TABLE IF NOT EXISTS fleet.shift_templates (
    id          UUID PRIMARY KEY,
    company_id  UUID NOT NULL REFERENCES fleet.companies(id) ON DELETE CASCADE,
    name        VARCHAR(60) NOT NULL,
    start_time  TIME NOT NULL,
    end_time    TIME NOT NULL,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_shift_template_name ON fleet.shift_templates (company_id, LOWER(name));

INSERT INTO fleet.shift_templates (id, company_id, name, start_time, end_time, sort_order)
SELECT gen_random_uuid(), c.id, t.name, t.start_time, t.end_time, t.sort_order
FROM fleet.companies c
CROSS JOIN (VALUES ('Matin', TIME '06:00', TIME '14:00', 1),
                   ('Journée', TIME '08:00', TIME '17:00', 2),
                   ('Après-midi', TIME '14:00', TIME '22:00', 3),
                   ('Nuit', TIME '22:00', TIME '06:00', 4)) AS t(name, start_time, end_time, sort_order)
ON CONFLICT DO NOTHING;
