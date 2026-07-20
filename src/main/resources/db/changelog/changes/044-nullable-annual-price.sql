--liquibase formatted sql
--changeset fleet-subscription:nullable-annual-price-v1 splitStatements:true

-- Le prix annuel est une option commerciale (facturation mensuelle seule
-- acceptée) : le formulaire d'édition envoie explicitement null quand aucun
-- prix annuel n'est défini, ce que la contrainte NOT NULL rejetait avec une
-- violation d'intégrité (PUT /api/v1/admin/super/plans/{id} -> 400).
ALTER TABLE fleet.subscription_plans
    ALTER COLUMN annual_price DROP NOT NULL,
    ALTER COLUMN annual_price DROP DEFAULT;
