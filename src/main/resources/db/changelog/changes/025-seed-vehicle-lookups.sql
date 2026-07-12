--liquibase formatted sql
--changeset gabriel:seed-vehicle-lookups-v1 splitStatements:true

-- 1a. Types de véhicules
INSERT INTO fleet.vehicle_types (id, code, label, description) VALUES
  ('00000001-0000-4000-8000-000000000001', 'CAR',     'Voiture',    'Véhicule léger de tourisme'),
  ('00000001-0000-4000-8000-000000000002', 'TRUCK',   'Camion',     'Poids lourd / camion de livraison'),
  ('00000001-0000-4000-8000-000000000003', 'MINIBUS', 'Minibus',    'Transport de personnes ≤ 25 places'),
  ('00000001-0000-4000-8000-000000000004', 'VAN',     'Fourgon',    'Utilitaire léger'),
  ('00000001-0000-4000-8000-000000000005', 'MOTO',    'Moto',       'Deux roues motorisé')
ON CONFLICT (code) DO NOTHING;

-- 1b. Constructeurs
INSERT INTO fleet.manufacturers (id, code, label, description) VALUES
  ('00000002-0000-4000-8000-000000000001', 'TOYOTA',      'Toyota',      'Constructeur japonais'),
  ('00000002-0000-4000-8000-000000000002', 'MERCEDES',    'Mercedes-Benz','Constructeur allemand'),
  ('00000002-0000-4000-8000-000000000003', 'MITSUBISHI',  'Mitsubishi',  'Constructeur japonais'),
  ('00000002-0000-4000-8000-000000000004', 'RENAULT',     'Renault',     'Constructeur français'),
  ('00000002-0000-4000-8000-000000000005', 'FORD',        'Ford',        'Constructeur américain'),
  ('00000002-0000-4000-8000-000000000006', 'ISUZU',       'Isuzu',       'Constructeur japonais')
ON CONFLICT (code) DO NOTHING;

-- 1c. Marques
INSERT INTO fleet.brands (id, code, label, description) VALUES
  ('00000003-0000-4000-8000-000000000001', 'TOYOTA',      'Toyota',      'Marque japonaise'),
  ('00000003-0000-4000-8000-000000000002', 'MERCEDES',    'Mercedes',    'Marque allemande'),
  ('00000003-0000-4000-8000-000000000003', 'MITSUBISHI',  'Mitsubishi',  'Marque japonaise'),
  ('00000003-0000-4000-8000-000000000004', 'RENAULT',     'Renault',     'Marque française'),
  ('00000003-0000-4000-8000-000000000005', 'FORD',        'Ford',        'Marque américaine'),
  ('00000003-0000-4000-8000-000000000006', 'ISUZU',       'Isuzu',       'Marque japonaise')
ON CONFLICT (code) DO NOTHING;

-- 1d. Modèles
INSERT INTO fleet.vehicle_models (id, code, label, description) VALUES
  ('00000004-0000-4000-8000-000000000001', 'HILUX',    'Hilux',      'Toyota Hilux pick-up'),
  ('00000004-0000-4000-8000-000000000002', 'COROLLA',  'Corolla',    'Toyota Corolla berline'),
  ('00000004-0000-4000-8000-000000000003', 'ACTROS',   'Actros',     'Mercedes Actros poids lourd'),
  ('00000004-0000-4000-8000-000000000004', 'SPRINTER', 'Sprinter',   'Mercedes Sprinter fourgon'),
  ('00000004-0000-4000-8000-000000000005', 'L200',     'L200',       'Mitsubishi L200 pick-up'),
  ('00000004-0000-4000-8000-000000000006', 'MASTER',   'Master',     'Renault Master utilitaire'),
  ('00000004-0000-4000-8000-000000000007', 'TRANSIT',  'Transit',    'Ford Transit fourgon'),
  ('00000004-0000-4000-8000-000000000008', 'NPR',      'NPR',        'Isuzu NPR camion léger')
ON CONFLICT (code) DO NOTHING;

-- 1e. Gabarits
INSERT INTO fleet.vehicle_sizes (id, code, label, description) VALUES
  ('00000005-0000-4000-8000-000000000001', 'SMALL',  'Petit',  'Véhicule compact'),
  ('00000005-0000-4000-8000-000000000002', 'MEDIUM', 'Moyen',  'Véhicule intermédiaire'),
  ('00000005-0000-4000-8000-000000000003', 'LARGE',  'Grand',  'Grand véhicule / poids lourd')
ON CONFLICT (code) DO NOTHING;

-- 1f. Types d'usage
INSERT INTO fleet.usage_types (id, code, label, description) VALUES
  ('00000006-0000-4000-8000-000000000001', 'COMMERCIAL', 'Commercial',  'Usage professionnel / livraison'),
  ('00000006-0000-4000-8000-000000000002', 'PERSONAL',   'Personnel',   'Usage personnel'),
  ('00000006-0000-4000-8000-000000000003', 'MIXED',      'Mixte',       'Usage mixte pro/perso')
ON CONFLICT (code) DO NOTHING;

-- 1g. Types de carburant
INSERT INTO fleet.fuel_types (id, code, label, description) VALUES
  ('00000007-0000-4000-8000-000000000001', 'PETROL',   'Essence',     'Carburant essence'),
  ('00000007-0000-4000-8000-000000000002', 'DIESEL',   'Diesel',      'Gazole'),
  ('00000007-0000-4000-8000-000000000003', 'HYBRID',   'Hybride',     'Motorisation hybride'),
  ('00000007-0000-4000-8000-000000000004', 'ELECTRIC', 'Électrique',  'Motorisation 100% électrique')
ON CONFLICT (code) DO NOTHING;

-- 1h. Transmissions
INSERT INTO fleet.transmission_types (id, code, label, description) VALUES
  ('00000008-0000-4000-8000-000000000001', 'MANUAL',    'Manuelle',    'Boîte manuelle'),
  ('00000008-0000-4000-8000-000000000002', 'AUTOMATIC', 'Automatique', 'Boîte automatique'),
  ('00000008-0000-4000-8000-000000000003', 'CVT',       'CVT',         'Transmission à variation continue')
ON CONFLICT (code) DO NOTHING;

-- 1i. Couleurs
INSERT INTO fleet.vehicle_colors (id, code, label, description) VALUES
  ('00000009-0000-4000-8000-000000000001', 'BLANC',   'Blanc',   'Blanc uni'),
  ('00000009-0000-4000-8000-000000000002', 'NOIR',    'Noir',    'Noir'),
  ('00000009-0000-4000-8000-000000000003', 'ROUGE',   'ROUGE',   'Rouge'),
  ('00000009-0000-4000-8000-000000000004', 'BLEU',    'Bleu',    'Bleu'),
  ('00000009-0000-4000-8000-000000000005', 'GRIS',    'Gris',    'Gris'),
  ('00000009-0000-4000-8000-000000000006', 'ARGENT',  'Argent',  'Argent métallisé'),
  ('00000009-0000-4000-8000-000000000007', 'VERT',    'Vert',    'Vert'),
  ('00000009-0000-4000-8000-000000000008', 'ORANGE',  'Orange',  'Orange')
ON CONFLICT (code) DO NOTHING;
