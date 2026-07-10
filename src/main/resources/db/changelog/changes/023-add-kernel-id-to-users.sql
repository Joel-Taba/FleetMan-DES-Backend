--liquibase formatted sql
--changeset gabriel:add-kernel-id-to-users splitStatements:true

ALTER TABLE fleet.users ADD COLUMN IF NOT EXISTS kernel_id UUID;
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_kernel_id ON fleet.users(kernel_id) WHERE kernel_id IS NOT NULL;
