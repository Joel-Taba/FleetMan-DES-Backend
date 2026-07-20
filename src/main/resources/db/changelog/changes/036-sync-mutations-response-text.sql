--liquibase formatted sql
--changeset fleet-sync:sync-mutations-response-text-v1 splitStatements:true

-- R2DBC mappe response_body en VARCHAR ; JSONB provoquait des erreurs à l'INSERT/UPDATE.
ALTER TABLE fleet.sync_mutations
    ALTER COLUMN response_body TYPE TEXT USING response_body::text;
