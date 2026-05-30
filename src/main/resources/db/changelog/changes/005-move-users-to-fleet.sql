--liquibase formatted sql

--changeset gabriel:move-users-to-fleet-schema-v5 splitStatements:false
--comment: Migration vers fleet.users utilisant l'email comme username pour garantir l'unicité et l'accès

-- 3. BLOC PROCÉDURAL POUR LA MIGRATION
DO $$ 
BEGIN 
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'users') THEN
        
        -- On migre en utilisant l'email_address comme USERNAME pour éviter les doublons de noms
        IF EXISTS (SELECT FROM information_schema.columns 
                   WHERE table_schema = 'public' AND table_name = 'users' AND column_name = 'email_address') THEN
            
            EXECUTE 'INSERT INTO fleet.users (id, username, email, first_name)
                     SELECT id, email_address, email_address, name FROM public.users
                     ON CONFLICT (id) DO NOTHING';
                     
        ELSIF EXISTS (SELECT FROM information_schema.columns 
                      WHERE table_schema = 'public' AND table_name = 'users' AND column_name = 'email') THEN
                  
            EXECUTE 'INSERT INTO fleet.users (id, username, email, first_name)
                     SELECT id, email, email, name FROM public.users
                     ON CONFLICT (id) DO NOTHING';
        END IF;
    END IF;

    -- Redirection des contraintes (Fleet Managers)
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_schema = 'fleet' AND table_name = 'fleet_managers') THEN
        BEGIN
            ALTER TABLE fleet.fleet_managers DROP CONSTRAINT IF EXISTS fk_manager_user_fleet;
        EXCEPTION WHEN OTHERS THEN NULL; END;
        ALTER TABLE fleet.fleet_managers ADD CONSTRAINT fk_manager_user_fleet FOREIGN KEY (user_id) REFERENCES fleet.users(id) ON DELETE CASCADE;
    END IF;

    -- Redirection des contraintes (Drivers)
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_schema = 'fleet' AND table_name = 'drivers') THEN
        BEGIN
            ALTER TABLE fleet.drivers DROP CONSTRAINT IF EXISTS fk_driver_user_fleet;
        EXCEPTION WHEN OTHERS THEN NULL; END;
        ALTER TABLE fleet.drivers ADD CONSTRAINT fk_driver_user_fleet FOREIGN KEY (user_id) REFERENCES fleet.users(id) ON DELETE CASCADE;
    END IF;

END $$;