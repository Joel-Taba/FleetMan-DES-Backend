--liquibase formatted sql
--changeset fleet-kpi:add-yearly-period-type splitStatements:true

-- Ajout de la granularité YEARLY aux snapshots KPI
ALTER TABLE fleet.kpi_snapshots
    DROP CONSTRAINT IF EXISTS kpi_snapshots_period_type_check;

ALTER TABLE fleet.kpi_snapshots
    ADD CONSTRAINT kpi_snapshots_period_type_check
    CHECK (period_type IN ('DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY'));

CREATE INDEX IF NOT EXISTS idx_kpi_yearly_fleet
    ON fleet.kpi_snapshots(fleet_id, period_start DESC)
    WHERE period_type = 'YEARLY';
