--liquibase formatted sql
--changeset fleet-sync:sync-mutations-v1 splitStatements:true

CREATE TABLE IF NOT EXISTS fleet.sync_mutations (
    client_mutation_id UUID PRIMARY KEY,
    user_id            UUID NOT NULL,
    http_method        VARCHAR(10) NOT NULL,
    endpoint           VARCHAR(512) NOT NULL,
    request_hash       VARCHAR(64),
    response_status    INT,
    response_body      JSONB,
    entity_id          UUID,
    processed_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sync_mutations_user ON fleet.sync_mutations(user_id);
