--liquibase formatted sql

--changeset fleet:028-kernel-resource-id
--comment: Lien véhicules locaux ↔ ressources Kernel resource-core
ALTER TABLE fleet.vehicles ADD COLUMN IF NOT EXISTS kernel_resource_id UUID;
CREATE UNIQUE INDEX IF NOT EXISTS uq_vehicles_kernel_resource_id
    ON fleet.vehicles (kernel_resource_id) WHERE kernel_resource_id IS NOT NULL;
