-- =====================================================
-- YOWYOB DB - INIT SCRIPT
-- PostgreSQL
-- Structure: Core -> Organization -> RBAC
-- =====================================================

-- Start a transaction for safety
BEGIN;

-- Fresh start
DROP SCHEMA IF EXISTS public CASCADE;
CREATE SCHEMA public;

-- Publish query paths
SET search_path TO public;

-- Extension for UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =====================================================
-- ENUM TYPES
-- =====================================================

CREATE TYPE status_enum AS ENUM (
  'DRAFT', 'WAITING_CONFIRMATION', 'PUBLISHED', 'IN_PROGRESS', 'CANCELLED', 'EXPIRED'
);

CREATE TYPE category_enum AS ENUM (
  'ANNOUNCE', 'PLANNING', 'VEHICLE', 'ADDRESS', 'EXPERIENCE'
);

CREATE TYPE action_enum AS ENUM (
  'CREATE', 'UPDATE', 'READ', 'DELETE'
);

CREATE TYPE vehicle_type_enum AS ENUM (
  'CAR', 'VAN', 'TRUCK', 'BIKE'
);

CREATE TYPE offer_state_enum AS ENUM (
  'NEW', 'PENDING', 'CHOSEN', 'TERMINATED', 'CANCELED'
);

CREATE TYPE ride_state_enum AS ENUM (
  'CONFIRMED', 'ONGOING', 'COMPLETED', 'CANCELED'
);

CREATE TYPE event_type_enum AS ENUM (
  'ENTRY', 'EXIT'
);

CREATE TYPE maintenance_status_enum AS ENUM (
  'UP_TO_DATE', 'PENDING', 'OVERDUE'
);

CREATE TYPE engine_status_enum AS ENUM (
  'OK', 'NEED_SERVICE', 'OUT_OF_SERVICE'
);

CREATE TYPE role_type_enum AS ENUM (
  'CUSTOMER', 'DRIVER', 'FLEET_MANAGER', 'ADMIN', 'PASSENGER', 'PRESIDENT', 'MODERATOR', 'CLIENT'
);

CREATE TYPE type_enum AS ENUM (
  'NOTIFICATION', 'CHAT', 'PAYMENT', 'MEDIA'
);

CREATE TYPE reaction_type_enum AS ENUM (
  'LIKE', 'LOVE', 'HAHA', 'WOW', 'SAD', 'ANGRY'
);

-- =====================================================
-- CORE TABLES
-- =====================================================

CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  name TEXT NOT NULL,
  phone_number TEXT,
  email_address TEXT UNIQUE
);

CREATE TABLE images (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  url TEXT NOT NULL,
  alt_text TEXT,
  uploaded_at TIMESTAMP DEFAULT now()
);

CREATE TABLE countries (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  name TEXT NOT NULL,
  code TEXT NOT NULL UNIQUE,
  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP DEFAULT now()
);

CREATE TABLE business_actors (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  name TEXT,
  phone_number TEXT,
  email_address TEXT,
  CONSTRAINT fk_business_actor_user
    FOREIGN KEY (id) REFERENCES users(id)
    ON DELETE CASCADE
);

-- =====================================================
-- ORGANIZATION TABLES
-- =====================================================

CREATE TABLE organizations (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  business_actor_id UUID REFERENCES business_actors(id),
  logo_id UUID REFERENCES images(id),

  code TEXT,
  service TEXT,
  is_individual_business BOOLEAN DEFAULT false,
  email TEXT,
  short_name TEXT,
  long_name TEXT,
  description TEXT,
  logo_uri TEXT,
  website_url TEXT,
  social_network TEXT,
  business_registration_number TEXT,
  tax_number TEXT,
  capital_share NUMERIC,
  ceo_name TEXT,
  year_founded INT,
  keywords TEXT,
  number_of_employees INT,
  legal_form TEXT,
  is_active BOOLEAN DEFAULT true,
  status status_enum DEFAULT 'DRAFT',

  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP DEFAULT now(),
  deleted_at TIMESTAMP
);

CREATE TABLE agencies (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE, -- Must belong to an org

  owner_id UUID REFERENCES business_actors(id),
  manager_id UUID REFERENCES business_actors(id),
  logo_id UUID REFERENCES images(id),

  code TEXT,
  name TEXT,
  location TEXT,
  description TEXT,
  transferable BOOLEAN DEFAULT false,
  is_active BOOLEAN DEFAULT true,
  logo_uri TEXT,
  short_name TEXT,
  long_name TEXT,
  is_individual_business BOOLEAN DEFAULT false,
  is_headquarter BOOLEAN DEFAULT false,
  country TEXT,
  city TEXT,
  latitude NUMERIC,
  longitude NUMERIC,
  open_time TIME,
  close_time TIME,
  phone TEXT,
  email TEXT,
  whatsapp TEXT,
  greeting_message TEXT,
  average_revenue NUMERIC,
  capital_share NUMERIC,
  registration_number TEXT,
  social_network TEXT,
  tax_number TEXT,
  keywords TEXT,
  is_public BOOLEAN DEFAULT false,
  is_business BOOLEAN DEFAULT true,
  total_affiliated_customers INT DEFAULT 0,

  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP DEFAULT now(),
  deleted_at TIMESTAMP
);

CREATE TABLE business_domains (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  organization_id UUID REFERENCES organizations(id) ON DELETE SET NULL, -- optional link to organization
  parent_id UUID REFERENCES business_domains(id) ON DELETE SET NULL,   -- self-referential for hierarchy
  image_id UUID REFERENCES images(id),

  code TEXT,
  service TEXT,
  name TEXT,
  image_uri TEXT,
  type TEXT,
  type_label TEXT,
  description TEXT,

  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP DEFAULT now(),
  deleted_at TIMESTAMP
);

CREATE TABLE organization_business_domains (
  organization_id UUID REFERENCES organizations(id) ON DELETE CASCADE,
  business_domain_id UUID REFERENCES business_domains(id) ON DELETE CASCADE,
  PRIMARY KEY (organization_id, business_domain_id)
);

CREATE TABLE contacts (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  contactable_id UUID REFERENCES organizations(id) ON DELETE CASCADE,

  contactable_type TEXT,
  first_name TEXT,
  last_name TEXT,
  title TEXT,
  is_email_verified BOOLEAN DEFAULT false,
  is_phone_number_verified BOOLEAN DEFAULT false,
  is_favorite BOOLEAN DEFAULT false,
  phone_number TEXT,
  secondary_phone_number TEXT,
  fax_number TEXT,
  email TEXT,
  secondary_email TEXT,
  email_verified_at TIMESTAMP,
  phone_verified_at TIMESTAMP,

  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP DEFAULT now(),
  deleted_at TIMESTAMP
);

CREATE TABLE certifications (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  organization_id UUID REFERENCES organizations(id) ON DELETE CASCADE,

  type TEXT,
  name TEXT,
  description TEXT,
  obtainment_date DATE,

  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP DEFAULT now(),
  deleted_at TIMESTAMP
);

CREATE TABLE third_parties (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  organization_id UUID REFERENCES organizations(id) ON DELETE CASCADE,
  logo_id UUID REFERENCES images(id),

  code TEXT,
  type TEXT,
  legal_form TEXT,
  unique_identification_number TEXT,
  trade_registration_number TEXT,
  name TEXT,
  acronym TEXT,
  long_name TEXT,
  logo_uri TEXT,
  accounting_account_numbers TEXT,
  authorized_payment_methods TEXT,
  authorized_credit_limit NUMERIC,
  max_discount_rate NUMERIC,
  vat_subject BOOLEAN,
  operations_balance NUMERIC,
  opening_balance NUMERIC,
  pay_term_number INT,
  pay_term_type TEXT,
  third_party_family TEXT,
  classification TEXT,
  tax_number TEXT,
  loyalty_points NUMERIC,
  loyalty_points_used NUMERIC,
  loyalty_points_expired NUMERIC,
  enabled BOOLEAN DEFAULT true,

  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP DEFAULT now(),
  deleted_at TIMESTAMP
);

CREATE TABLE settings (
  user_id UUID PRIMARY KEY REFERENCES users(id),
  theme TEXT,
  language TEXT,
  long_ride_enabled BOOLEAN DEFAULT false,
  short_ride_enabled BOOLEAN DEFAULT false,
  privacy_enable BOOLEAN DEFAULT true,
  allow_calls BOOLEAN DEFAULT true,
  allow_messages BOOLEAN DEFAULT true,
  notify_new_rides BOOLEAN DEFAULT true,
  notify_ratings BOOLEAN DEFAULT true,
  notify_practical_tips BOOLEAN DEFAULT true,
  notify_promotions BOOLEAN DEFAULT true,
  notify_policy_updates BOOLEAN DEFAULT true,
  notify_peak_hour_recommendations BOOLEAN DEFAULT true,
  receive_email BOOLEAN DEFAULT true,
  receive_sms BOOLEAN DEFAULT true,
  receive_push_notifications BOOLEAN DEFAULT true,
  receive_whatsapp BOOLEAN DEFAULT true,

  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP DEFAULT now()
);

CREATE TABLE proposed_activities (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  organization_id UUID REFERENCES organizations(id) ON DELETE CASCADE,

  type TEXT,
  name TEXT,
  rate NUMERIC,
  description TEXT,

  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP DEFAULT now()
);

CREATE TABLE addresses (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  addressable_id UUID REFERENCES organizations(id) ON DELETE CASCADE,
  country_id UUID REFERENCES countries(id) ON DELETE SET NULL,

  addressable_type TEXT,
  type TEXT,
  address_line_1 TEXT,
  address_line_2 TEXT,
  city TEXT,
  state TEXT,
  locality TEXT,
  zip_code TEXT,
  postal_code TEXT,
  po_box TEXT,
  is_default BOOLEAN DEFAULT false,
  neighbor_hood TEXT,
  informal_description TEXT,
  latitude NUMERIC,
  longitude NUMERIC,

  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP DEFAULT now(),
  deleted_at TIMESTAMP
);

-- =====================================================
-- USERS EXTENDED
-- =====================================================

CREATE TABLE providers (
  id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,

  code TEXT,
  contact_info TEXT,
  address TEXT,
  is_active BOOLEAN DEFAULT true,
  product_service_type TEXT,

  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP DEFAULT now(),
  deleted_at TIMESTAMP
);

CREATE TABLE employees (
  id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,

  code TEXT,
  is_manager BOOLEAN DEFAULT false,
  role TEXT,
  department TEXT,

  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP DEFAULT now(),
  deleted_at TIMESTAMP
);

CREATE TABLE prospects (
  id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,

  code TEXT,
  payment_method TEXT,
  amount_paid NUMERIC,
  interest_level TEXT,

  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP DEFAULT now()
);

CREATE TABLE sales_persons (
  id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,

  code TEXT,
  commission_rate NUMERIC,
  credit NUMERIC,
  current_balance NUMERIC,

  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP DEFAULT now(),
  deleted_at TIMESTAMP
);

CREATE TABLE customers (
  id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,

  code TEXT,
  payment_method TEXT,
  amount_paid NUMERIC,

  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP DEFAULT now(),
  deleted_at TIMESTAMP
);

CREATE TABLE services (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

  name TEXT,
  phone_number TEXT,
  email_address TEXT
);

-- =====================================================
-- RIDE & FLEET MANAGEMENT
-- =====================================================

CREATE TABLE drivers (
  id UUID PRIMARY KEY REFERENCES business_actors(id) ON DELETE CASCADE,

  status TEXT,
  license_number TEXT,
  has_car BOOLEAN DEFAULT false
);

CREATE TABLE offers (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  passenger_id UUID REFERENCES customers(id) ON DELETE CASCADE,

  start_point TEXT,
  end_point TEXT,
  price NUMERIC,
  state offer_state_enum DEFAULT 'NEW',
  ids_interested_drivers TEXT,

  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP DEFAULT now()
);

CREATE TABLE rides (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  offer_id UUID REFERENCES offers(id) ON DELETE SET NULL,
  passenger_id UUID REFERENCES users(id) ON DELETE SET NULL,
  driver_id UUID REFERENCES drivers(id) ON DELETE SET NULL,

  distance NUMERIC,
  time_estimation NUMERIC,
  real_time NUMERIC,
  state ride_state_enum DEFAULT 'CONFIRMED',

  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP DEFAULT now()
);

CREATE TABLE fleet_managers (
  id UUID PRIMARY KEY REFERENCES business_actors(id) ON DELETE CASCADE,

  name TEXT,
  phone_number TEXT,
  email_address TEXT
);

CREATE TABLE fleets (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  fleet_manager_id UUID REFERENCES fleet_managers(id) ON DELETE SET NULL,

  name TEXT,
  phone_number TEXT,
  email_address TEXT,

  created_at TIMESTAMP DEFAULT now()
);

CREATE TABLE geofence_zones (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

  surface_area NUMERIC,
  perimeter NUMERIC
);

CREATE TABLE vehicles (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  driver_id UUID REFERENCES drivers(id) ON DELETE SET NULL,
  fleet_id UUID REFERENCES fleets(id) ON DELETE SET NULL,
  zone_id UUID REFERENCES geofence_zones(id) ON DELETE SET NULL,

  license_plate TEXT,
  model TEXT,
  brand TEXT,
  manufacturing_year INT,
  type vehicle_type_enum,
  color TEXT
);

CREATE TABLE roads (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

  start_point TEXT,
  end_point TEXT
);

CREATE TABLE trips (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  road_id UUID REFERENCES roads(id) ON DELETE SET NULL,
  driver_id UUID REFERENCES drivers(id) ON DELETE SET NULL,

  start_date DATE,
  end_date DATE,
  start_time TIME,
  end_time TIME,
  type TEXT,
  color TEXT
);

CREATE TABLE operational_parameters (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  vehicle_id UUID REFERENCES vehicles(id) ON DELETE CASCADE,
  trip_id UUID REFERENCES trips(id) ON DELETE CASCADE,

  statut TEXT,
  current_location TEXT,
  current_speed NUMERIC,
  fuel_level NUMERIC,
  mileage NUMERIC,
  odometer_reading NUMERIC,
  bearing NUMERIC,

  timestamp TIMESTAMP DEFAULT now()
);

CREATE TABLE financial_parameters (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  vehicle_id UUID REFERENCES vehicles(id) ON DELETE CASCADE,
  trip_id UUID REFERENCES trips(id) ON DELETE CASCADE,

  insurance_number TEXT,
  insurance_expired_at DATE,
  registered_at DATE,
  purchased_at DATE,
  depreciation_rate NUMERIC,
  cost_per_km NUMERIC
);

CREATE TABLE maintenance_parameters (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  vehicle_id UUID REFERENCES vehicles(id) ON DELETE CASCADE,
  trip_id UUID REFERENCES trips(id) ON DELETE CASCADE,

  last_maintenance_at DATE,
  next_maintenance_at DATE,
  engine_status engine_status_enum DEFAULT 'OK',
  battery_health TEXT,
  maintenance_status maintenance_status_enum DEFAULT 'UP_TO_DATE'
);

CREATE TABLE geofence_points (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

  latitude NUMERIC,
  longitude NUMERIC
);

CREATE TABLE geofence_point_zone_linkages (
  point_id UUID REFERENCES geofence_points(id) ON DELETE CASCADE,
  zone_id UUID REFERENCES geofence_zones(id) ON DELETE CASCADE,
  PRIMARY KEY (point_id, zone_id)
);

CREATE TABLE geofence_events (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  vehicle_id UUID REFERENCES vehicles(id) ON DELETE CASCADE,
  zone_id UUID REFERENCES geofence_zones(id) ON DELETE SET NULL,
  point_id UUID REFERENCES geofence_points(id) ON DELETE SET NULL,

  type event_type_enum,

  timestamp TIMESTAMP DEFAULT now()
);

CREATE TABLE offer_driver_linkages (
  offer_id UUID REFERENCES offers(id) ON DELETE CASCADE,
  driver_id UUID REFERENCES drivers(id) ON DELETE CASCADE,
  PRIMARY KEY (offer_id, driver_id),

  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP DEFAULT now()
);

-- =====================================================
-- OTHERS: PRODUCTS, SYNDICATS, PUBLICATIONS & REVIEWS
-- =====================================================

CREATE TABLE syndicats (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  organization_id UUID REFERENCES organizations(id) ON DELETE CASCADE,

  is_approved BOOLEAN DEFAULT false,
  name TEXT,
  description TEXT,
  domain TEXT,
  type TEXT,
  charte_url TEXT,
  status_url TEXT,
  members_list_url TEXT,
  commitment_certificate_url TEXT,

  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP DEFAULT now()
);

CREATE TABLE abstract_products (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  syndicat_id UUID REFERENCES syndicats(id) ON DELETE SET NULL,

  name TEXT,
  description TEXT,

  created_at TIMESTAMP DEFAULT now()
);

CREATE TABLE products (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  organization_id UUID REFERENCES organizations(id) ON DELETE CASCADE,
  abstract_product_id UUID REFERENCES abstract_products(id) ON DELETE SET NULL,

  name TEXT,
  description TEXT,
  is_active BOOLEAN DEFAULT true,
  standard_price NUMERIC,
  departure_location TEXT,
  arrival_location TEXT,
  start_date DATE,
  start_time TIME,
  end_date DATE,
  end_time TIME,
  baggage_info TEXT,
  is_negotiable BOOLEAN DEFAULT false,
  payment_method TEXT,
  title TEXT,
  status status_enum DEFAULT 'DRAFT',
  product_urls TEXT,
  regular_amount NUMERIC,
  discount_percentage NUMERIC,
  discounted_amount NUMERIC,
  metadata JSONB,

  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP DEFAULT now()
);

CREATE TABLE publication_votes (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

  title TEXT,
  description TEXT,
  closing_at TIMESTAMP,
  type category_enum
);

CREATE TABLE votes (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  publication_vote_id UUID REFERENCES publication_votes(id) ON DELETE CASCADE,

  label TEXT
);

CREATE TABLE avis (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  product_id UUID REFERENCES products(id) ON DELETE CASCADE,
  syndicat_id UUID REFERENCES syndicats(id) ON DELETE SET NULL,

  comment TEXT,
  number INT,

  created_at TIMESTAMP DEFAULT now()
);

CREATE TABLE branches (
  id UUID PRIMARY KEY REFERENCES agencies(id),
  syndicat_id UUID REFERENCES syndicats(id) ON DELETE SET NULL,

  name TEXT,
  location TEXT,
  contact TEXT,

  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP DEFAULT now()
);

CREATE TABLE publications (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,
  author_id UUID REFERENCES users(id) ON DELETE SET NULL,

  content TEXT,
  status status_enum DEFAULT 'DRAFT',
  n_likes INT DEFAULT 0,

  created_at TIMESTAMP DEFAULT now()
);

CREATE TABLE publication_images (
  publication_id UUID REFERENCES publications(id) ON DELETE CASCADE,
  image_id UUID REFERENCES images(id) ON DELETE CASCADE,
  PRIMARY KEY (publication_id, image_id),

  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP DEFAULT now()
);

CREATE TABLE events (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  branch_id UUID REFERENCES branches(id) ON DELETE SET NULL,

  title TEXT,
  description TEXT,
  location TEXT,
  date DATE,
  start_time TIME,
  end_time TIME,

  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP DEFAULT now()
);

CREATE TABLE event_images (
  event_id UUID REFERENCES events(id) ON DELETE CASCADE,
  image_id UUID REFERENCES images(id) ON DELETE CASCADE,
  PRIMARY KEY (event_id, image_id),

  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP DEFAULT now()
);

CREATE TABLE reactions (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  publication_id UUID REFERENCES publications(id) ON DELETE CASCADE,
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,

  type reaction_type_enum,

  reacted_at TIMESTAMP DEFAULT now()
);

CREATE TABLE comments (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  author_id UUID REFERENCES users(id) ON DELETE SET NULL,
  publication_id UUID REFERENCES publications(id) ON DELETE CASCADE,
  parent_id UUID REFERENCES comments(id) ON DELETE SET NULL,
  image_id UUID REFERENCES images(id) ON DELETE SET NULL,

  content TEXT,

  created_at TIMESTAMP DEFAULT now()
);

CREATE TABLE profiles (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,

  first_name TEXT,
  last_name TEXT,
  nickname TEXT,
  profile_image_url TEXT,
  birth_date DATE,
  nationality TEXT,
  gender TEXT,
  language TEXT,
  company_name TEXT,
  biography TEXT,
  rating NUMERIC,
  total_trips INT,
  is_available BOOLEAN DEFAULT true,
  is_verified BOOLEAN DEFAULT false,

  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP DEFAULT now()
);

-- =====================================================
-- PAYMENTS & SUBSCRIPTIONS
-- =====================================================

CREATE TABLE admins (
  id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,

  name TEXT,
  phone_number TEXT,
  email_address TEXT
);

CREATE TABLE subscriptions (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  admin_id UUID REFERENCES admins(id) ON DELETE SET NULL,

  label TEXT,
  price NUMERIC,
  duration_in_days INT,
  description TEXT,
  is_active BOOLEAN DEFAULT true,

  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP DEFAULT now()
);

CREATE TABLE payments (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  driver_id UUID REFERENCES drivers(id) ON DELETE SET NULL,
  subscription_id UUID REFERENCES subscriptions(id) ON DELETE SET NULL,
  user_id UUID REFERENCES business_actors(id) ON DELETE SET NULL,

  amount_paid NUMERIC,
  status TEXT,
  id_provider_transaction TEXT,

  created_at TIMESTAMP DEFAULT now()
);

CREATE TABLE reviews (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  ride_id UUID REFERENCES rides(id) ON DELETE CASCADE,
  author_id UUID REFERENCES users(id) ON DELETE SET NULL,

  subject TEXT,
  comment TEXT,
  rating NUMERIC,

  created_at TIMESTAMP DEFAULT now()
);

-- =====================================================
-- ROLE-BASED ACCESS CONTROL (RBAC)
-- =====================================================

CREATE TABLE roles (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

  name TEXT NOT NULL,
  guard_name TEXT,

  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP DEFAULT now()
);

CREATE TABLE permissions (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

  name TEXT NOT NULL,
  guard_name TEXT,

  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP DEFAULT now()
);

CREATE TABLE role_has_permissions (
  role_id UUID REFERENCES roles(id) ON DELETE CASCADE,
  permission_id UUID REFERENCES permissions(id) ON DELETE CASCADE,
  PRIMARY KEY (role_id, permission_id),

  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP DEFAULT now()
);

CREATE TABLE user_has_roles (
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  role_id UUID REFERENCES roles(id) ON DELETE CASCADE,
  PRIMARY KEY (user_id, role_id),

  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP DEFAULT now()
);

-- Commit the transaction
COMMIT;

-- =====================================================
-- END OF SCRIPT. NOTHING AFTER HERE!
-- =====================================================
