--liquibase formatted sql
--changeset fleet-settings:app-settings-v1 splitStatements:true

CREATE TABLE IF NOT EXISTS fleet.app_settings (
    setting_key   VARCHAR(100) PRIMARY KEY,
    setting_value TEXT NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO fleet.app_settings (setting_key, setting_value)
VALUES ('subscription_grace_days', '7')
ON CONFLICT (setting_key) DO NOTHING;
