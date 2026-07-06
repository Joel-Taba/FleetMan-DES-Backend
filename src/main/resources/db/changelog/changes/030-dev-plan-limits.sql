--liquibase formatted sql
--changeset fleet-subscription:dev-plan-limits-v1 splitStatements:true

-- Augmente la limite Starter pour faciliter les tests (front + back)
UPDATE fleet.subscription_plans
SET max_vehicles = 50,
    updated_at   = now()
WHERE name = 'Starter';
