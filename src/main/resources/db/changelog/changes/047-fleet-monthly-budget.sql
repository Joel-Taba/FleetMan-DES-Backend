--liquibase formatted sql
--changeset fleet-admin:fleet-monthly-budget-v1 splitStatements:true

-- Budget mensuel alloué à la flotte, saisi par l'administrateur à la création.
-- Distinct du module fleet.budgets (suivi mensuel détaillé côté gestionnaire,
-- avec alertes 80%/100%) : ce champ sert de valeur cible par défaut, visible
-- et éditable côté administrateur, y compris avant qu'un gestionnaire ne soit
-- assigné à la flotte.
ALTER TABLE fleet.fleets
    ADD COLUMN monthly_budget NUMERIC(14, 2);
