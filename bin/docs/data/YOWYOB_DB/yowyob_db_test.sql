-- =====================================================
-- YOWYOB DB - TEST QUERIES (SEMI-VALIDATION)
-- PostgreSQL
-- =====================================================

-- Summary Table to display what will be tested
SELECT * FROM (VALUES
    ('Test 1.1', 'Check Driver Inheritance (Name, Status, Car)'),
    ('Test 1.2', 'Check Customers Specific Data (Code, Payment)'),
    ('Test 2.1', 'User Role Assignments'),
    ('Test 3.1', 'Agency Locations & Headquarter Status'),
    ('Test 4.1', 'Vehicle Inventory (Fleet vs Independent)'),
    ('Test 5.1', 'Full Ride History (Passenger + Driver + Price)'),
    ('Test 5.2', 'Pending Offers (No Ride Created Yet)'),
    ('Test 6.1', 'Active Products by Organization'),
    ('Test 7.1', 'Publications Feed with Likes'),
    ('Test 7.2', 'Customer Reviews on Rides'),
    ('Test 8.1', 'Payment & Subscription Log')
) AS summary_table (Test_ID, Test_Description);

-- =====================================================
-- BLOCK 1: CORE & INHERITANCE VERIFICATION
-- =====================================================

-- 1.1. Check Driver Inheritance
SELECT 'Check Driver Inheritance (Name, Status, Car)' AS "TEST 1.1";
SELECT 
    u.id,
    u.name AS full_name,
    u.phone_number,
    d.status AS driver_status,
    d.license_number,
    d.has_car
FROM drivers d
JOIN business_actors ba ON d.id = ba.id
JOIN users u ON ba.id = u.id
LIMIT 5;

-- 1.2. Check Customers and Payment Methods
SELECT 'Check Customers Specific Data' AS "TEST 1.2";
SELECT 
    u.name AS customer_name,
    c.code AS customer_code,
    c.payment_method
FROM customers c
JOIN users u ON c.id = u.id
LIMIT 5;

-- =====================================================
-- BLOCK 2: RBAC (ROLES & SECURITY)
-- =====================================================

-- 2.1. Who has which role?
SELECT 'User Role Assignments' AS "TEST 2.1";
SELECT 
    u.name AS user_name,
    u.email_address,
    r.name AS assigned_role
FROM users u
JOIN user_has_roles uhr ON u.id = uhr.user_id
JOIN roles r ON uhr.role_id = r.id
ORDER BY u.name ASC
LIMIT 5;

-- =====================================================
-- BLOCK 3: ORGANIZATION & HIERARCHY
-- =====================================================

-- 3.1. Hierarchy Organization -> Agencies
SELECT 'Agency Locations & Headquarter Status' AS "TEST 3.1";
SELECT 
    org.short_name AS organization,
    a.name AS agency_name,
    a.city,
    a.is_headquarter
FROM agencies a
JOIN organizations org ON a.organization_id = org.id
ORDER BY org.short_name
LIMIT 5;

-- =====================================================
-- BLOCK 4: FLEET MANAGEMENT
-- =====================================================

-- 4.1. Vehicle Inventory
SELECT 'Vehicle Inventory (Fleet vs Independent)' AS "TEST 4.1";
SELECT 
    v.license_plate,
    v.brand || ' ' || v.model AS vehicle_model,
    v.type,
    f.name AS fleet_owner,
    u_owner.name AS independent_owner
FROM vehicles v
LEFT JOIN fleets f ON v.fleet_id = f.id
LEFT JOIN users u_owner ON v.user_id = u_owner.id
LIMIT 5;

-- =====================================================
-- BLOCK 5: RIDES & OFFERS (CORE BUSINESS)
-- =====================================================

-- 5.1. Full Ride Details
SELECT 'Full Ride History (Passenger + Driver + Price)' AS "TEST 5.1";
SELECT 
    r.id AS ride_id,
    r.state AS ride_state,
    passenger_u.name AS passenger,
    driver_u.name AS driver,
    o.start_point,
    o.end_point,
    o.price AS agreed_price,
    r.distance || ' km' AS real_distance
FROM rides r
JOIN offers o ON r.offer_id = o.id
JOIN customers c ON r.passenger_id = c.id
JOIN users passenger_u ON c.id = passenger_u.id
JOIN drivers d ON r.driver_id = d.id
JOIN users driver_u ON d.id = driver_u.id
WHERE r.state = 'COMPLETED'
LIMIT 5;

-- 5.2. Offers without Drivers
SELECT 'Pending Offers (No Ride Created Yet)' AS "TEST 5.2";
SELECT 
    o.id,
    o.state,
    o.price,
    o.created_at
FROM offers o
LEFT JOIN rides r ON o.id = r.offer_id
WHERE r.id IS NULL
LIMIT 5;

-- =====================================================
-- BLOCK 6: PRODUCTS & SERVICES
-- =====================================================

-- 6.1. Product Catalog by Organization
SELECT 'Active Products by Organization' AS "TEST 6.1";
SELECT 
    org.short_name AS seller,
    p.name AS product_name,
    p.standard_price AS price,
    p.status
FROM products p
JOIN organizations org ON p.organization_id = org.id
WHERE p.is_active = true
LIMIT 5;

-- =====================================================
-- BLOCK 7: SOCIAL (PUBLICATIONS & REVIEWS)
-- =====================================================

-- 7.1. Branch News Feed
SELECT 'Publications Feed with Likes' AS "TEST 7.1";
SELECT 
    b.name AS source_branch,
    u.name AS author,
    pub.content AS message,
    pub.n_likes AS likes
FROM publications pub
JOIN branches b ON pub.branch_id = b.id
JOIN users u ON pub.author_id = u.id
ORDER BY pub.created_at DESC
LIMIT 5;

-- 7.2. Ride Reviews
SELECT 'Customer Reviews on Rides' AS "TEST 7.2";
SELECT 
    rev.rating,
    rev.comment,
    u.name AS author
FROM reviews rev
JOIN users u ON rev.author_id = u.id
LIMIT 5;

-- =====================================================
-- BLOCK 8: FINANCE (SUBSCRIPTIONS & PAYMENTS)
-- =====================================================

-- 8.1. Payment History
SELECT 'Payment & Subscription Log' AS "TEST 8.1";
SELECT 
    pay.id AS payment_id,
    u.name AS payer,
    sub.label AS subscription_plan,
    pay.amount_paid,
    pay.status,
    pay.created_at
FROM payments pay
JOIN users u ON pay.user_id = u.id
JOIN subscriptions sub ON pay.subscription_id = sub.id
ORDER BY pay.created_at DESC
LIMIT 5;

-- =====================================================
-- END OF SCRIPT. NOTHING AFTER HERE!
-- =====================================================