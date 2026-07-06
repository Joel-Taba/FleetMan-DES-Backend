--liquibase formatted sql

--changeset fleet:029-kernel-file-id
--comment: Référence fichiers légaux ↔ file-core Kernel
ALTER TABLE fleet.vehicle_documents ADD COLUMN IF NOT EXISTS kernel_file_id UUID;
ALTER TABLE fleet.driver_documents ADD COLUMN IF NOT EXISTS kernel_file_id UUID;
ALTER TABLE fleet.uploaded_files ADD COLUMN IF NOT EXISTS kernel_file_id UUID;
