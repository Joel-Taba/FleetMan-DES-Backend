--liquibase formatted sql

-- Organisation de l'entreprise :
--   * fiche légale (raison sociale, forme juridique, RCCM, NIU, secteur, coordonnées) portée par
--     fleet.companies, modifiable par l'administrateur seul ;
--   * fleet.company_sites : siège, dépôts, agences et ateliers de l'entreprise ;
--   * fleets.site_id : une flotte est basée sur un site (facultatif).
-- Le nom recopié sur les gestionnaires est réaligné sur celui de l'entreprise.

--changeset fleetman:063-company-legal-profile
ALTER TABLE fleet.companies
    ADD COLUMN IF NOT EXISTS legal_name          VARCHAR(200),
    ADD COLUMN IF NOT EXISTS legal_form          VARCHAR(30),
    ADD COLUMN IF NOT EXISTS registration_number VARCHAR(50),
    ADD COLUMN IF NOT EXISTS tax_id              VARCHAR(50),
    ADD COLUMN IF NOT EXISTS sector              VARCHAR(40),
    ADD COLUMN IF NOT EXISTS address             VARCHAR(255),
    ADD COLUMN IF NOT EXISTS city                VARCHAR(100),
    ADD COLUMN IF NOT EXISTS country             VARCHAR(2) NOT NULL DEFAULT 'CM',
    ADD COLUMN IF NOT EXISTS contact_phone       VARCHAR(30),
    ADD COLUMN IF NOT EXISTS contact_email       VARCHAR(150),
    ADD COLUMN IF NOT EXISTS website             VARCHAR(200);

--changeset fleetman:063-company-sites
CREATE TABLE IF NOT EXISTS fleet.company_sites (
    id           UUID PRIMARY KEY,
    company_id   UUID NOT NULL REFERENCES fleet.companies(id) ON DELETE CASCADE,
    name         VARCHAR(120) NOT NULL,
    site_type    VARCHAR(20)  NOT NULL
        CHECK (site_type IN ('HEAD_OFFICE', 'DEPOT', 'AGENCY', 'WORKSHOP')),
    address      VARCHAR(255),
    city         VARCHAR(100) NOT NULL,
    phone        VARCHAR(30),
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_company_site_name ON fleet.company_sites (company_id, LOWER(name));
CREATE UNIQUE INDEX IF NOT EXISTS uq_company_head_office ON fleet.company_sites (company_id) WHERE site_type = 'HEAD_OFFICE';

--changeset fleetman:063-fleet-site
ALTER TABLE fleet.fleets ADD COLUMN IF NOT EXISTS site_id UUID REFERENCES fleet.company_sites(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_fleets_site ON fleet.fleets (site_id);

--changeset fleetman:063-align-manager-company-name
UPDATE fleet.fleet_managers fm SET company_name = LEFT(c.name, 100)
FROM fleet.companies c WHERE c.id = fm.created_by_admin_id AND fm.company_name IS DISTINCT FROM LEFT(c.name, 100);
