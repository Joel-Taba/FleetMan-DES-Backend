--liquibase formatted sql
--changeset fleet-users:fix-manager1-driver-identity-v1 splitStatements:true runOnChange:true

-- BUG : manager1@fleetman.cm et driver@fleetman.cm ont été provisionnés
-- directement côté Kernel via des scripts shell (kernel_provision_users.sh),
-- en contournant le flux d'inscription applicatif (qui capture explicitement
-- firstName/lastName/phone). Or le Kernel n'expose ces informations ni dans
-- le JWT ni via GET /api/users/me (vérifié en direct : seuls username/email
-- y figurent) — la synchronisation "juste-à-temps" à la connexion ne peut
-- donc jamais les déduire automatiquement. Résultat : ces deux comptes de
-- démo affichaient des pages Organisation / Profil totalement vides.
--
-- On restaure ici leur identité documentée (kernel_provision_users.sh /
-- kernel_provision_demo_fleet.sh : "Jean Dupont" et "André Mbarga").
UPDATE fleet.users
SET first_name = 'Jean', last_name = 'Dupont', phone = '+237677000100'
WHERE id = 'e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb' AND email = 'manager1@fleetman.cm';

UPDATE fleet.users
SET first_name = 'André', last_name = 'Mbarga', phone = '+237677000200'
WHERE id = '35944e04-43c1-4eba-8acf-13f72a3ca5be' AND email = 'driver@fleetman.cm';
