-- Profil demo uniquement (DevDemoDataSeeder) — idempotent.
-- Le contrôle du permis (DRV_009/010/011) bloque désormais trajets et affectations : chaque
-- chauffeur de démo reçoit un permis valide couvrant les catégories des véhicules de démo,
-- pour que la démonstration ne soit pas bloquée. Les permis réels saisis ne sont pas modifiés.
UPDATE fleet.driver_documents
SET license_categories = 'B,C,D'
WHERE doc_type = 'DRIVING_LICENSE'
  AND (license_categories IS NULL OR license_categories = '');

INSERT INTO fleet.driver_documents (id, driver_id, doc_type, doc_number, license_categories, issuer,
                                    issue_date, expiry_date, status, notes)
SELECT gen_random_uuid(), d.user_id, 'DRIVING_LICENSE', 'DEMO-' || left(d.user_id::text, 8), 'B,C,D',
       'MINTRANS', DATE '2022-01-01', DATE '2031-12-31', 'VALID', 'Permis de démonstration'
FROM fleet.drivers d
WHERE NOT EXISTS (
    SELECT 1 FROM fleet.driver_documents dd
    WHERE dd.driver_id = d.user_id AND dd.doc_type = 'DRIVING_LICENSE'
);
