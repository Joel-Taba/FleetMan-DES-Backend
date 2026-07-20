#!/usr/bin/env bash
# =============================================================================
# seed_kyc_test_subscriber.sh — Insère un demandeur PENDING + 4 documents KYC
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd "$ROOT/.." && pwd)"
DOCS_SRC="$REPO_ROOT/fleet_management_front/public/documents"
UPLOADS="$ROOT/uploads"
DB_CONTAINER="${DB_CONTAINER:-fleet-management-db}"
DB_USER="${DB_USER:-fleet_admin}"
DB_NAME="${DB_NAME:-fleetmanBD}"

USER_ID="f47ac10b-58cc-4372-a567-0e02b2c3d479"
DOC_CNI_ID="a1111111-1111-4111-8111-111111111101"
DOC_CR_ID="a1111111-1111-4111-8111-111111111102"
DOC_DOM_ID="a1111111-1111-4111-8111-111111111103"
DOC_OTHER_ID="a1111111-1111-4111-8111-111111111104"

require_file() {
  local f="$1"
  if [[ ! -f "$f" ]]; then
    echo "❌ Fichier manquant : $f" >&2
    exit 1
  fi
}

require_file "$DOCS_SRC/CNI.pdf"
require_file "$DOCS_SRC/sample-vehicle-doc.pdf"
require_file "$DOCS_SRC/Samuel.png"
require_file "$DOCS_SRC/taba.png"

mkdir -p "$UPLOADS"
cp "$DOCS_SRC/CNI.pdf" "$UPLOADS/sub-kyc-cni.pdf"
cp "$DOCS_SRC/sample-vehicle-doc.pdf" "$UPLOADS/sub-kyc-casier.pdf"
cp "$DOCS_SRC/Samuel.png" "$UPLOADS/sub-kyc-samuel.png"
cp "$DOCS_SRC/taba.png" "$UPLOADS/sub-kyc-taba.png"

PLAN_ID=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -A \
  -c "SELECT id FROM fleet.subscription_plans WHERE is_active = true ORDER BY monthly_price LIMIT 1;" \
  | head -1)

if [[ -z "$PLAN_ID" ]]; then
  echo "❌ Aucun plan actif trouvé en base." >&2
  exit 1
fi

docker exec -i "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" <<SQL
INSERT INTO fleet.users (
  id, username, email, first_name, last_name, is_active, approval_status, kernel_id
) VALUES (
  '$USER_ID',
  'samuel.kyc',
  'samuel.kyc@fleetman.cm',
  'Samuel',
  'Nkomo',
  false,
  'PENDING',
  NULL
) ON CONFLICT (id) DO UPDATE SET
  username = EXCLUDED.username,
  email = EXCLUDED.email,
  first_name = EXCLUDED.first_name,
  last_name = EXCLUDED.last_name,
  is_active = false,
  approval_status = 'PENDING';

INSERT INTO fleet.fleet_managers (user_id, company_name, subscription_status, requested_plan_id)
VALUES ('$USER_ID', 'KYC Test Transport', 'PENDING', '$PLAN_ID')
ON CONFLICT (user_id) DO UPDATE SET
  company_name = EXCLUDED.company_name,
  subscription_status = 'PENDING',
  requested_plan_id = EXCLUDED.requested_plan_id;

DELETE FROM fleet.subscription_documents WHERE user_id = '$USER_ID';

INSERT INTO fleet.subscription_documents (
  id, user_id, doc_type, doc_number, file_url, file_mime_type, file_original_name, issuer, created_at
) VALUES
  ('$DOC_CNI_ID', '$USER_ID', 'ID_CARD', 'CNI-998877665',
   '/api/v1/files/sub-kyc-cni.pdf', 'application/pdf', 'CNI.pdf', 'État civil', NOW()),
  ('$DOC_CR_ID', '$USER_ID', 'CRIMINAL_RECORD', 'CJ-2026-KYC-01',
   '/api/v1/files/sub-kyc-casier.pdf', 'application/pdf', 'sample-vehicle-doc.pdf', 'Tribunal', NOW()),
  ('$DOC_DOM_ID', '$USER_ID', 'DOMICILE_PROOF', 'DOM-2026-42',
   '/api/v1/files/sub-kyc-samuel.png', 'image/png', 'Samuel.png', 'Propriétaire', NOW()),
  ('$DOC_OTHER_ID', '$USER_ID', 'OTHER', 'DOC-KYC-04',
   '/api/v1/files/sub-kyc-taba.png', 'image/png', 'taba.png', NULL, NOW());
SQL

echo ""
echo "✅ Demandeur KYC de test chargé"
echo "   ID        : $USER_ID"
echo "   Nom       : Samuel Nkomo"
echo "   Email     : samuel.kyc@fleetman.cm"
echo "   Entreprise: KYC Test Transport"
echo "   Documents : CNI.pdf, sample-vehicle-doc.pdf, Samuel.png, taba.png"
echo ""
echo "   Vue super-admin :"
echo "   http://localhost:3001/dashboard/super-admin/subscriptions/$USER_ID"
