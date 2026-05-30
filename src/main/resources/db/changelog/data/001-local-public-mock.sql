--liquibase formatted sql

--changeset gabriel:mock-structure-v2 context:local
--comment: Alignement du mock local sur la structure réelle de la DB YOWYOB (email_address)

-- 1. Structure PUBLIC (Simulation Auth Service réel)
CREATE TABLE IF NOT EXISTS public.users (
    id UUID PRIMARY KEY,
    name TEXT,
    email_address TEXT, -- Changé de 'email' à 'email_address'
    password TEXT
);

CREATE TABLE IF NOT EXISTS public.roles (
    name VARCHAR(50) PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS public.user_has_roles (
    user_id UUID REFERENCES public.users(id),
    role_name VARCHAR(50) REFERENCES public.roles(name),
    PRIMARY KEY (user_id, role_name)
);

-- 2. Insertion des Rôles
INSERT INTO public.roles (name) VALUES 
('FLEET_ADMIN'), 
('FLEET_MANAGER'), 
('FLEET_DRIVER')
ON CONFLICT DO NOTHING;

-- 3. Insertion des Types de Véhicules
INSERT INTO fleet.vehicle_types (id, code, label, description) VALUES 
('11111111-1111-1111-1111-111111111111', 'CAR', 'Voiture', 'Véhicule léger de tourisme'),
('22222222-2222-2222-2222-222222222222', 'TRUCK', 'Camion', 'Poids lourd pour logistique'),
('33333333-3333-3333-3333-333333333333', 'MOTO', 'Moto', 'Deux roues pour livraison rapide'),
('44444444-4444-4444-4444-444444444444', 'VAN', 'Fourgon', 'Transport de matériel')
ON CONFLICT DO NOTHING;