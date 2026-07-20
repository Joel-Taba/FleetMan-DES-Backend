#!/usr/bin/env bash
# Provisionne la flotte démo FleetMan dans le Kernel : utilisateurs, rôles, acteurs, véhicules.
# Usage : ./scripts/kernel_provision_demo_fleet.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$SCRIPT_DIR"

if [ -z "${OWNER_TOKEN:-}" ] || [ -z "${KERNEL:-}" ]; then
  # shellcheck source=/dev/null
  source "$SCRIPT_DIR/kernel_token.sh" >/dev/null
fi

: "${OWNER_TOKEN:?OWNER_TOKEN requis}"
: "${KERNEL:?}"
: "${CID:?}"
: "${KEY:?}"
: "${FLEET_TENANT_ID:?}"
: "${FLEET_ORG_ID:?}"

DEMO_PASSWORD="${DEMO_PASSWORD:-FleetMan2026!}"
ROLE_MANAGER_ID="${ROLE_MANAGER_ID:-7f19aa8a-bec7-485e-82ad-853b43b68e6c}"
ROLE_DRIVER_ID="${ROLE_DRIVER_ID:-00ed9f2f-e59b-42db-8c8f-9a0eb20ceb8d}"
ROLE_ADMIN_ID="${ROLE_ADMIN_ID:-}"

H=(-H "Content-Type: application/json" -H "Authorization: Bearer $OWNER_TOKEN"
    -H "X-Client-Id: $CID" -H "X-Api-Key: $KEY"
    -H "X-Tenant-Id: $FLEET_TENANT_ID" -H "X-Organization-Id: $FLEET_ORG_ID")

log() { echo "→ $*"; }
ok() { echo "  ✅ $*"; }
warn() { echo "  ⚠️  $*"; }

resolve_admin_role() {
  if [ -n "$ROLE_ADMIN_ID" ]; then
    echo "$ROLE_ADMIN_ID"
    return
  fi
  curl -s "$KERNEL/api/administration/roles" "${H[@]}" \
    | python3 -c "
import sys,json
d=json.load(sys.stdin)
for r in d.get('data') or []:
    if r.get('name') == 'FLEET_ADMIN' or r.get('code') == 'FLEET_ADMIN':
        print(r.get('id') or r.get('roleId') or '')
        break
" 2>/dev/null || true
}

register_or_find_user() {
  local email="$1" username="$2" phone="$3"
  local resp user_id
  resp=$(curl -s -X POST "$KERNEL/api/auth/register" "${H[@]}" \
    -d "{\"username\":\"$username\",\"email\":\"$email\",\"phoneNumber\":\"$phone\",\"password\":\"$DEMO_PASSWORD\",\"authProvider\":\"LOCAL\"}")
  user_id=$(echo "$resp" | python3 -c "
import sys,json
d=json.load(sys.stdin)
if d.get('success') and d.get('data'):
    print(d['data'].get('id',''))
else:
    print('')
" 2>/dev/null || echo "")
  if [ -n "$user_id" ]; then
    ok "Inscrit $email → $user_id" >&2
    echo "$user_id"
    return
  fi
  # Utilisateur existant : discover-contexts pour récupérer userId
  local disc
  disc=$(curl -s -X POST "$KERNEL/api/auth/discover-contexts" \
    -H "Content-Type: application/json" -H "X-Client-Id: $CID" -H "X-Api-Key: $KEY" \
    -d "{\"principal\":\"$email\",\"password\":\"$DEMO_PASSWORD\"}")
  user_id=$(echo "$disc" | python3 -c "
import sys,json
d=json.load(sys.stdin)
for c in (d.get('data') or {}).get('contexts') or []:
    if c.get('userId'):
        print(c['userId']); break
" 2>/dev/null || echo "")
  if [ -n "$user_id" ]; then
    ok "Existant $email → $user_id" >&2
    echo "$user_id"
  else
    warn "Impossible de résoudre $email"
    echo ""
  fi
}

assign_role() {
  local user_id="$1" role_id="$2" label="$3"
  [ -z "$user_id" ] && return 0
  log "Rôle $label pour $user_id"
  curl -s -X POST "$KERNEL/api/administration/users/$user_id/roles" "${H[@]}" \
    -d "{\"roleId\":\"$role_id\",\"scopeType\":\"TENANT\",\"scopeId\":\"$FLEET_TENANT_ID\",\"scope\":\"TENANT\"}" \
    | python3 -c "
import sys,json
d=json.load(sys.stdin)
msg=(d.get('message') or '')[:80]
print(' ', d.get('success'), msg)
" || true
}

link_actor() {
  local email="$1" first="$2" last="$3"
  log "Acteur $email"
  local resp actor_id
  resp=$(curl -s -X POST "$KERNEL/api/actors" "${H[@]}" \
    -d "{\"organizationId\":\"$FLEET_ORG_ID\",\"firstName\":\"$first\",\"lastName\":\"$last\",\"email\":\"$email\",\"type\":\"EMPLOYEE\"}")
  actor_id=$(echo "$resp" | python3 -c "import sys,json; d=json.load(sys.stdin); print((d.get('data') or {}).get('id') or '')" 2>/dev/null || echo "")
  if [ -z "$actor_id" ]; then
    warn "Acteur non créé ($(echo "$resp" | python3 -c "import sys,json; print(json.load(sys.stdin).get('message','')[:60])" 2>/dev/null))"
    return 0
  fi
  curl -s -X POST "$KERNEL/api/organizations/$FLEET_ORG_ID/actors" "${H[@]}" \
    -d "{\"actorId\":\"$actor_id\",\"type\":\"EMPLOYEE\"}" \
    | python3 -c "import sys,json; d=json.load(sys.stdin); print('  link:', d.get('success'), (d.get('message') or '')[:50])" || true
}

resolve_agency_id() {
  curl -s "$KERNEL/api/organizations/$FLEET_ORG_ID/agencies" "${H[@]}" \
    | python3 -c "
import sys,json
d=json.load(sys.stdin)
agencies=d.get('data') or []
if agencies:
    print(agencies[0].get('id',''))
" 2>/dev/null || echo ""
}

register_vehicle_resource() {
  local code="$1" name="$2" plate="$3" agency_id="$4"
  log "Ressource véhicule $code ($plate)"
  curl -s -X POST "$KERNEL/api/organizations/$FLEET_ORG_ID/resources" "${H[@]}" \
    -d "{\"agencyId\":\"$agency_id\",\"resourceCode\":\"$code\",\"name\":\"$name\",\"category\":\"VEHICLE\",\"serialNumber\":\"$plate\"}" \
    | python3 -c "
import sys,json
d=json.load(sys.stdin)
print(' ', d.get('success'), (d.get('message') or '')[:60], (d.get('data') or {}).get('id',''))
" || true
}

echo ""
echo "=== PROVISION FLOTTE DÉMO KERNEL ==="
echo "org=$FLEET_ORG_ID tenant=$FLEET_TENANT_ID"

ROLE_ADMIN_ID="$(resolve_admin_role)"
[ -n "$ROLE_ADMIN_ID" ] && ok "FLEET_ADMIN role=$ROLE_ADMIN_ID" || warn "FLEET_ADMIN role introuvable"

# Comptes connus (UUID Kernel)
ADMIN_ID="96b87460-6179-483d-a6d5-9cbcacd9d06d"
MANAGER1_ID="e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb"
DRIVER1_ID="35944e04-43c1-4eba-8acf-13f72a3ca5be"

MANAGER2_ID=$(register_or_find_user "manager2@fleetman.cm" "manager.foka" "+237677000102")
DRIVER2_ID=$(register_or_find_user "driver2@fleetman.cm" "driver.kouam" "+237677000201")
DRIVER3_ID=$(register_or_find_user "driver3@fleetman.cm" "driver.nana" "+237677000202")
DRIVER4_ID=$(register_or_find_user "driver4@fleetman.cm" "driver.fouda" "+237677000203")
DRIVER5_ID=$(register_or_find_user "driver5@fleetman.cm" "driver.bella" "+237677000204")

[ -n "$ROLE_ADMIN_ID" ] && assign_role "$ADMIN_ID" "$ROLE_ADMIN_ID" "FLEET_ADMIN"
assign_role "$MANAGER1_ID" "$ROLE_MANAGER_ID" "FLEET_MANAGER"
assign_role "$MANAGER2_ID" "$ROLE_MANAGER_ID" "FLEET_MANAGER"
assign_role "$DRIVER1_ID" "$ROLE_DRIVER_ID" "FLEET_DRIVER"
assign_role "$DRIVER2_ID" "$ROLE_DRIVER_ID" "FLEET_DRIVER"
assign_role "$DRIVER3_ID" "$ROLE_DRIVER_ID" "FLEET_DRIVER"
assign_role "$DRIVER4_ID" "$ROLE_DRIVER_ID" "FLEET_DRIVER"
assign_role "$DRIVER5_ID" "$ROLE_DRIVER_ID" "FLEET_DRIVER"

link_actor "admin@fleetman.cm" "Marie" "Admin"
link_actor "manager1@fleetman.cm" "Jean" "Dupont"
link_actor "manager2@fleetman.cm" "Paul" "Foka"
link_actor "driver@fleetman.cm" "André" "Mbarga"
link_actor "driver2@fleetman.cm" "Paul" "Kouam"
link_actor "driver3@fleetman.cm" "Amina" "Nana"
link_actor "driver4@fleetman.cm" "Eric" "Fouda"
link_actor "driver5@fleetman.cm" "Claire" "Bella"

AGENCY_ID=$(resolve_agency_id)
if [ -z "$AGENCY_ID" ]; then
  warn "Aucune agence — création FLEET-DEPOT"
  AGENCY_ID=$(curl -s -X POST "$KERNEL/api/organizations/$FLEET_ORG_ID/agencies" "${H[@]}" \
    -d '{"code":"FLEET-DEPOT","name":"Dépôt FleetMan","type":"WAREHOUSE"}' \
    | python3 -c "import sys,json; d=json.load(sys.stdin); print((d.get('data') or {}).get('id',''))" 2>/dev/null || echo "")
fi
[ -n "$AGENCY_ID" ] && ok "Agence=$AGENCY_ID" || warn "Pas d'agence — véhicules Kernel ignorés"

if [ -n "$AGENCY_ID" ]; then
  register_vehicle_resource "VH-FM-001" "Toyota Hilux" "LT-892-CE" "$AGENCY_ID"
  register_vehicle_resource "VH-FM-002" "Mercedes Actros" "CE-456-AB" "$AGENCY_ID"
  register_vehicle_resource "VH-FM-003" "Toyota Corolla" "SW-123-DL" "$AGENCY_ID"
  register_vehicle_resource "VH-FM-004" "Isuzu NPR" "LT-445-CE" "$AGENCY_ID"
  register_vehicle_resource "VH-FM-005" "Hyundai H100" "CE-789-AB" "$AGENCY_ID"
fi

echo ""
echo "=== Acteurs org (aperçu) ==="
curl -s "$KERNEL/api/organizations/$FLEET_ORG_ID/actors" "${H[@]}" \
  | python3 -c "
import sys,json
d=json.load(sys.stdin)
actors=d.get('data') or []
print(f'Total acteurs: {len(actors)}')
for a in actors[:12]:
    print(' -', a.get('email') or a.get('id'), a.get('firstName'), a.get('lastName'))
" 2>/dev/null || true

echo ""
echo "✅ Provision flotte démo terminée"
