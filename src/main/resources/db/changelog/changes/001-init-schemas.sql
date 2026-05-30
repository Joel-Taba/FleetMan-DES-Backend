--liquibase formatted sql

--changeset gabriel:create-fleet-schema
CREATE SCHEMA IF NOT EXISTS fleet;
