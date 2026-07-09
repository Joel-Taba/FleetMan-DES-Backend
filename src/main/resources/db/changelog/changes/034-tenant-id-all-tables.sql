-- Ajoute tenant_id à toutes les tables du schéma fleet (alignement kernel-core multi-tenant)
DO $$
DECLARE
    tbl RECORD;
    default_tenant UUID := 'f5b814d9-766e-4c87-91ec-d6c8e32cb56c';
BEGIN
    FOR tbl IN
        SELECT tablename
        FROM pg_tables
        WHERE schemaname = 'fleet'
    LOOP
        BEGIN
            EXECUTE format(
                'ALTER TABLE fleet.%I ADD COLUMN IF NOT EXISTS tenant_id UUID',
                tbl.tablename
            );
            EXECUTE format(
                'UPDATE fleet.%I SET tenant_id = $1 WHERE tenant_id IS NULL',
                tbl.tablename
            ) USING default_tenant;
        EXCEPTION
            WHEN OTHERS THEN
                RAISE NOTICE 'tenant_id skip table fleet.%: %', tbl.tablename, SQLERRM;
        END;
    END LOOP;
END $$;

-- Index sur les tables métier critiques
CREATE INDEX IF NOT EXISTS idx_users_tenant ON fleet.users(tenant_id);
CREATE INDEX IF NOT EXISTS idx_fleets_tenant ON fleet.fleets(tenant_id);
CREATE INDEX IF NOT EXISTS idx_vehicles_tenant ON fleet.vehicles(tenant_id);
CREATE INDEX IF NOT EXISTS idx_drivers_tenant ON fleet.drivers(tenant_id);
CREATE INDEX IF NOT EXISTS idx_trips_tenant ON fleet.trips(tenant_id);
