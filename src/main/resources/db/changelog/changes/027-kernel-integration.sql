--liquibase formatted sql

--changeset fleet:027-kernel-user-id
--comment: Lien utilisateurs locaux ↔ identifiants Kernel RT-Comops
ALTER TABLE fleet.users ADD COLUMN IF NOT EXISTS kernel_id UUID;
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_kernel_id ON fleet.users (kernel_id) WHERE kernel_id IS NOT NULL;

--changeset fleet:027-kernel-organization-id
--comment: Lien flottes locales ↔ organisation Kernel
ALTER TABLE fleet.fleets ADD COLUMN IF NOT EXISTS kernel_organization_id UUID;
