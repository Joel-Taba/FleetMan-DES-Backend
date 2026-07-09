--liquibase formatted sql
--changeset fleet-subscription:subscription-documents-v1 splitStatements:true

CREATE TABLE IF NOT EXISTS fleet.subscription_documents (
    id                  UUID PRIMARY KEY,
    user_id             UUID NOT NULL REFERENCES fleet.users(id) ON DELETE CASCADE,
    doc_type            VARCHAR(80) NOT NULL,
    doc_number          VARCHAR(100),
    file_url            TEXT NOT NULL,
    file_mime_type      VARCHAR(120),
    file_original_name  VARCHAR(255),
    expiry_date         DATE,
    issuer              VARCHAR(150),
    issue_date          DATE,
    notes               TEXT,
    license_categories  VARCHAR(50),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_subscription_documents_user
    ON fleet.subscription_documents(user_id);

ALTER TABLE fleet.fleet_managers
    ADD COLUMN IF NOT EXISTS requested_plan_id UUID REFERENCES fleet.subscription_plans(id);
