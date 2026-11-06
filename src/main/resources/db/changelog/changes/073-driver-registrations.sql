--liquibase formatted sql

-- Inscription des chauffeurs en deux phases.
-- 1. Le chauffeur demande à rejoindre une flotte (code d'inscription de la flotte) avec ses
--    informations d'identification et un téléphone ou un e-mail pour recevoir son code.
-- 2. Le gestionnaire de la flotte accepte ou refuse ; un code d'activation à usage unique est
--    généré (72 h, 5 essais), envoyé par e-mail ou SMS ou remis en main propre.
-- 3. Le chauffeur active son compte (il choisit son mot de passe), puis complète son profil et
--    dépose ses pièces obligatoires avant d'accéder à son interface de travail.

--changeset fleetman:073-driver-registrations
ALTER TABLE fleet.fleets ADD COLUMN IF NOT EXISTS registration_code VARCHAR(12);
CREATE UNIQUE INDEX IF NOT EXISTS ux_fleets_registration_code ON fleet.fleets (registration_code) WHERE registration_code IS NOT NULL;

CREATE TABLE IF NOT EXISTS fleet.driver_registrations (
    id                 UUID PRIMARY KEY,
    fleet_id           UUID NOT NULL REFERENCES fleet.fleets (id),
    first_name         VARCHAR(100) NOT NULL,
    last_name          VARCHAR(100) NOT NULL,
    phone              VARCHAR(30),
    email              VARCHAR(255),
    licence_number     VARCHAR(50) NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    rejection_reason   TEXT,
    activation_code    VARCHAR(12),
    code_expires_at    TIMESTAMP,
    code_sent_at       TIMESTAMP,
    code_sent_channels VARCHAR(30),
    failed_attempts    INT NOT NULL DEFAULT 0,
    reviewed_by        UUID,
    reviewed_at        TIMESTAMP,
    driver_user_id     UUID,
    activated_at       TIMESTAMP,
    created_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    tenant_id          UUID,
    CONSTRAINT ck_driver_registrations_status CHECK (status IN ('PENDING', 'APPROVED', 'ACTIVATING', 'ACTIVATED', 'REJECTED')),
    CONSTRAINT ck_driver_registrations_contact CHECK (phone IS NOT NULL OR email IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS ix_driver_registrations_fleet_status ON fleet.driver_registrations (fleet_id, status);
CREATE INDEX IF NOT EXISTS ix_driver_registrations_phone ON fleet.driver_registrations (phone) WHERE phone IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_driver_registrations_email ON fleet.driver_registrations (LOWER(email)) WHERE email IS NOT NULL;

-- Profil complété par le chauffeur ; onboarding_completed_at NULL = interface de travail fermée.
ALTER TABLE fleet.drivers ADD COLUMN IF NOT EXISTS birth_date DATE;
ALTER TABLE fleet.drivers ADD COLUMN IF NOT EXISTS address VARCHAR(255);
ALTER TABLE fleet.drivers ADD COLUMN IF NOT EXISTS emergency_contact_name VARCHAR(120);
ALTER TABLE fleet.drivers ADD COLUMN IF NOT EXISTS emergency_contact_phone VARCHAR(30);
ALTER TABLE fleet.drivers ADD COLUMN IF NOT EXISTS onboarding_completed_at TIMESTAMP;
-- Les chauffeurs existants ont été créés par leur gestionnaire : ils gardent leur accès.
UPDATE fleet.drivers SET onboarding_completed_at = COALESCE(created_at, NOW()) WHERE onboarding_completed_at IS NULL;
