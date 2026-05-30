-- =====================================================
-- YOWYOB DB - DATA SEEDING SCRIPT
-- PostgreSQL
-- =====================================================

BEGIN;

-- =====================================================
-- 1. CLEANUP EXISTING DATA
-- =====================================================
TRUNCATE TABLE 
    users, images, countries, business_actors, 
    admins, fleet_managers, drivers, customers, 
    providers, employees, prospects, sales_persons,
    profiles, settings, addresses, contacts,
    roles, permissions, role_has_permissions, user_has_roles,
    organizations, agencies, business_domains, organization_business_domains,
    certifications, third_parties, proposed_activities, services, branches,
    fleets, vehicles, geofence_zones, geofence_points, geofence_events,
    roads, trips, offers, rides, reviews, offer_driver_linkages,
    operational_parameters, financial_parameters, maintenance_parameters,
    geofence_point_zone_linkages,
    syndicats, abstract_products, products, 
    publications, publication_images, publication_votes, votes,
    events, event_images, reactions, comments, avis,
    subscriptions, payments
    RESTART IDENTITY CASCADE;

-- =====================================================
-- 2. STATIC REFERENCE DATA
-- =====================================================

-- Countries
INSERT INTO countries (name, code) VALUES
('Cameroun', 'CM'), ('Sénégal', 'SN'), ('Côte d''Ivoire', 'CI'), 
('Gabon', 'GA'), ('Nigéria', 'NG'), ('Togo', 'TG');

-- Roles
INSERT INTO roles (name, guard_name) VALUES
('ADMIN', 'web'), ('FLEET_MANAGER', 'web'), ('DRIVER', 'web'), ('CUSTOMER', 'web');

-- Images (Random placeholders from Picsum)
INSERT INTO images (url, alt_text) 
SELECT 
    'https://picsum.photos/seed/' || i || '/800/600', 
    'Image aléatoire ' || i
FROM generate_series(1, 50) as i;

-- =====================================================
-- 3. USERS GENERATION (100 users)
-- =====================================================

INSERT INTO users (name, phone_number, email_address)
SELECT 
    (ARRAY['Mamadou', 'Jean-Pierre', 'Ibrahim', 'Fatou', 'Aminata', 'Ngolo', 'Aboubakar', 'Clarisse', 'Samuel', 'Kouamé', 'Aissatou', 'Bachelard', 'Landry', 'Thérèse'])[floor(random()*14)+1] 
    || ' ' || 
    (ARRAY['Diop', 'Njoya', 'Mbarga', 'Kone', 'Sow', 'Etoundi', 'Kamga', 'Traoré', 'Diallo', 'Fofana', 'Mensah', 'Atangana', 'Eto''o', 'Drogba'])[floor(random()*14)+1],
    (ARRAY['+237', '+221', '+225'])[floor(random()*3)+1] || (600000000 + floor(random()*99999999)::int),
    'user_' || i || '@yowyob.test'
FROM generate_series(1, 100) as i;

-- Settings & Profiles
INSERT INTO settings (user_id, theme, language, receive_push_notifications)
SELECT id, 'LIGHT', 'fr', true FROM users;

INSERT INTO profiles (user_id, first_name, nationality, is_verified)
SELECT id, split_part(name, ' ', 1), 'Camerounais', (random() > 0.5) FROM users;

-- =====================================================
-- 4. ACTORS DISPATCHING
-- User -> BusinessActor -> SpecificActor
-- =====================================================

-- 4.1 Admins (First 5 users)
INSERT INTO admins (id, name, email_address)
SELECT id, name, email_address FROM users ORDER BY email_address LIMIT 5;

-- 4.2 Fleet Managers (Next 5 users)
-- STEP 1: Insert into PARENT (BusinessActor)
INSERT INTO business_actors (id, name, phone_number, email_address)
SELECT id, name, phone_number, email_address FROM users ORDER BY email_address LIMIT 5 OFFSET 5;

-- STEP 2: Insert into CHILD (FleetManager)
INSERT INTO fleet_managers (id, name, email_address)
SELECT id, name, email_address FROM users ORDER BY email_address LIMIT 5 OFFSET 5;

-- 4.3 Drivers (Next 30 users)
-- STEP 1: Insert into PARENT (BusinessActor)
INSERT INTO business_actors (id, name, phone_number, email_address)
SELECT id, name, phone_number, email_address FROM users ORDER BY email_address LIMIT 30 OFFSET 10;

-- STEP 2: Insert into CHILD (Driver)
INSERT INTO drivers (id, status, license_number, has_car)
SELECT 
    id, 
    (ARRAY['AVAILABLE', 'BUSY', 'OFFLINE'])[floor(random()*3)+1], 
    'LIC-' || floor(random()*100000) || '-CM',
    (random() > 0.3)
FROM users ORDER BY email_address LIMIT 30 OFFSET 10;

-- 4.4 Customers (Remaining 60 users)
INSERT INTO customers (id, code, payment_method)
SELECT 
    id, 
    'CUST-' || substr(id::text, 1, 8),
    (ARRAY['CASH', 'MOBILE_MONEY', 'CARD'])[floor(random()*3)+1]
FROM users ORDER BY email_address LIMIT 60 OFFSET 40;

-- Assign Roles
INSERT INTO user_has_roles (user_id, role_id)
SELECT id, (SELECT id FROM roles WHERE name = 'ADMIN') FROM users ORDER BY email_address LIMIT 5;

INSERT INTO user_has_roles (user_id, role_id)
SELECT id, (SELECT id FROM roles WHERE name = 'FLEET_MANAGER') FROM users ORDER BY email_address LIMIT 5 OFFSET 5;

INSERT INTO user_has_roles (user_id, role_id)
SELECT id, (SELECT id FROM roles WHERE name = 'DRIVER') FROM users ORDER BY email_address LIMIT 30 OFFSET 10;

INSERT INTO user_has_roles (user_id, role_id)
SELECT id, (SELECT id FROM roles WHERE name = 'CUSTOMER') FROM users ORDER BY email_address LIMIT 60 OFFSET 40;

-- =====================================================
-- 5. ORGANIZATIONS & AGENCIES
-- =====================================================

INSERT INTO organizations (
    business_actor_id, logo_id, code, short_name, long_name, 
    description, tax_number, is_active, status
)
SELECT
    (SELECT id FROM fleet_managers ORDER BY random() LIMIT 1),
    (SELECT id FROM images ORDER BY random() LIMIT 1),
    'ORG-' || i,
    'Transport ' || i,
    'Société de Transport ' || i,
    'Description ' || i,
    'TAX-' || floor(random()*1000000),
    true,
    'PUBLISHED'
FROM generate_series(1, 35) as i;

INSERT INTO agencies (
    organization_id, manager_id, name, city, location, is_headquarter
)
SELECT 
    id, 
    (SELECT id FROM fleet_managers ORDER BY random() LIMIT 1),
    'Agence Centrale',
    (ARRAY['Douala', 'Yaoundé', 'Abidjan'])[floor(random()*3)+1],
    'Rue Principale',
    true
FROM organizations;

-- =====================================================
-- 6. FLEETS & VEHICLES
-- =====================================================

INSERT INTO fleets (fleet_manager_id, name, phone_number)
SELECT 
    id, 'Flotte de ' || name, '+237699000000'
FROM fleet_managers;

-- Vehicles
INSERT INTO vehicles (
    fleet_id, user_id, driver_id, license_plate, brand, model, type, color, manufacturing_year
)
SELECT 
    f.id,
    f.fleet_manager_id,
    (SELECT id FROM drivers ORDER BY random() LIMIT 1),
    'LT-' || floor(random()*999)::text || '-AA',
    (ARRAY['Toyota', 'Peugeot'])[floor(random()*2)+1],
    (ARRAY['Yaris', 'Partner'])[floor(random()*2)+1],
    (ARRAY['CAR', 'VAN'])[floor(random()*2)+1]::vehicle_type_enum,
    'Jaune',
    2020
FROM fleets f;

-- =====================================================
-- 7. OFFERS & RIDES
-- =====================================================

INSERT INTO offers (
    passenger_id, start_point, end_point, price, state, created_at
)
SELECT 
    (SELECT id FROM customers ORDER BY random() LIMIT 1),
    'Point A', 'Point B', 
    2000, 
    'CHOSEN',
    NOW()
FROM generate_series(1, 60) as i;

-- Rides
INSERT INTO rides (
    offer_id, passenger_id, driver_id, 
    distance, time_estimation, real_time, state, created_at
)
SELECT 
    id, -- offer_id
    passenger_id,
    (SELECT id FROM drivers ORDER BY random() LIMIT 1),
    15.5, 30, 35,
    'COMPLETED',
    created_at
FROM offers 
LIMIT 40;

-- Reviews
INSERT INTO reviews (ride_id, author_id, subject, comment, rating)
SELECT 
    id,
    passenger_id,
    'Chauffeur',
    'Bonne course, chauffeur ponctuel',
    5
FROM rides
WHERE state = 'COMPLETED';

-- =====================================================
-- 8. PRODUCTS
-- =====================================================

INSERT INTO products (
    organization_id, name, description, standard_price, 
    status, is_active, departure_location
)
SELECT 
    (SELECT id FROM organizations ORDER BY random() LIMIT 1),
    'Trajet Spécial ' || i,
    'Description produit',
    5000,
    'PUBLISHED',
    true,
    'Douala'
FROM generate_series(1, 40) as i;

-- =====================================================
-- 9. SUBSCRIPTIONS & PAYMENTS
-- =====================================================

INSERT INTO subscriptions (
    admin_id, label, price, duration_in_days, description, is_active
) VALUES 
((SELECT id FROM admins LIMIT 1), 'Pack Hebdo', 2500, 7, 'Standard', true),
((SELECT id FROM admins LIMIT 1), 'Pack Mensuel', 10000, 30, 'Pro', true),
((SELECT id FROM admins LIMIT 1), 'Pack Annuel', 100000, 365, 'VIP', true);

INSERT INTO payments (
    driver_id, user_id, subscription_id, amount_paid, status, id_provider_transaction, created_at
)
SELECT 
    d.id, -- Driver ID
    d.id, -- Payer ID
    s.id, -- Subscription ID
    s.price,
    'SUCCESS',
    'TXN-' || floor(random()*10000000),
    NOW()
FROM drivers d
CROSS JOIN subscriptions s
ORDER BY random()
LIMIT 50;

-- =====================================================
-- 10. SOCIAL & ADDRESSES
-- =====================================================

INSERT INTO syndicats (organization_id, name, is_approved)
SELECT id, 'Syndicat Transports', true FROM organizations LIMIT 1;

INSERT INTO branches (id, syndicat_id, name, location)
SELECT 
    ag.id,
    (SELECT id FROM syndicats LIMIT 1),
    'Branche ' || ag.name, 
    ag.location 
FROM agencies ag 
LIMIT 10;

INSERT INTO publications (branch_id, author_id, content, status, n_likes)
SELECT 
    (SELECT id FROM branches ORDER BY random() LIMIT 1),
    (SELECT id FROM admins ORDER BY random() LIMIT 1),
    'Info Trafic ' || i,
    'PUBLISHED',
    floor(random() * 50)::int
FROM generate_series(1, 40) as i;

COMMIT;