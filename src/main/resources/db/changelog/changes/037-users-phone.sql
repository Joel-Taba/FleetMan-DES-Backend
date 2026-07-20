--liquibase formatted sql
--changeset fleet-users:users-phone-v1 splitStatements:true

ALTER TABLE fleet.users
    ADD COLUMN IF NOT EXISTS phone VARCHAR(30);
