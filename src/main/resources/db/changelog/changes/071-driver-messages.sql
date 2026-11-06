--liquibase formatted sql

-- Messagerie gestionnaire <-> chauffeur : une conversation par chauffeur (avec le gestionnaire de sa flotte).
-- Un message « à toute la flotte » est une ligne par chauffeur, liées par broadcast_id.

--changeset fleetman:071-driver-messages
CREATE TABLE IF NOT EXISTS fleet.driver_messages (
    id               UUID PRIMARY KEY,
    fleet_id         UUID        NOT NULL REFERENCES fleet.fleets(id) ON DELETE CASCADE,
    thread_driver_id UUID        NOT NULL REFERENCES fleet.drivers(user_id) ON DELETE CASCADE,
    sender_id        UUID        NOT NULL,
    from_driver      BOOLEAN     NOT NULL,
    body             TEXT        NOT NULL,
    broadcast_id     UUID,
    read_at          TIMESTAMP,
    created_at       TIMESTAMP   NOT NULL DEFAULT NOW(),
    tenant_id        UUID
);
CREATE INDEX IF NOT EXISTS idx_driver_messages_thread ON fleet.driver_messages (thread_driver_id, created_at);
CREATE INDEX IF NOT EXISTS idx_driver_messages_fleet ON fleet.driver_messages (fleet_id, created_at);
