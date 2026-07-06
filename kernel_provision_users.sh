#!/usr/bin/env bash
# Provisionne / rattache les utilisateurs FleetMan dans le Kernel RT-Comops.
# Usage : ./kernel_provision_users.sh
# (charge automatiquement kernel_token.sh si les tokens ne sont pas exportés)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [ -z "${OWNER_TOKEN:-}" ] || [ -z "${KERNEL:-}" ]; then
  echo "🔑 Chargement des tokens Kernel (kernel_token.sh)..."
  # shellcheck source=/dev/null
  source "$SCRIPT_DIR/kernel_token.sh" >/dev/null
fi

: "${OWNER_TOKEN:?Échec obtention OWNER_TOKEN — vérifiez joeltaba4@gmail.com / FleetMan2026!}"
: "${KERNEL:?}"
: "${CID:?}"
: "${KEY:?}"
: "${FLEET_TENANT_ID:?}"
: "${FLEET_ORG_ID:?}"

MANAGER1_ID="${MANAGER1_ID:-e4da1dfe-c7cd-43a7-b633-231d2cf7d3fb}"
DRIVER_ID="${DRIVER_ID:-35944e04-43c1-4eba-8acf-13f72a3ca5be}"
ROLE_MANAGER_ID="${ROLE_MANAGER_ID:-7f19aa8a-bec7-485e-82ad-853b43b68e6c}"
ROLE_DRIVER_ID="${ROLE_DRIVER_ID:-00ed9f2f-e59b-42db-8c8f-9a0eb20ceb8d}"

H=(-H "Content-Type: application/json" -H "Authorization: Bearer $OWNER_TOKEN"
    -H "X-Client-Id: $CID" -H "X-Api-Key: $KEY"
    -H "X-Tenant-Id: $FLEET_TENANT_ID" -H "X-Organization-Id: $FLEET_ORG_ID")

kernel_json() {
  local resp="$1"
  if [ -z "$resp" ]; then
    echo "❌ Réponse vide du Kernel (réseau ou token expiré ?)" >&2
    return 1
  fi
  if ! echo "$resp" | python3 -c "import sys,json; json.load(sys.stdin)" 2>/dev/null; then
    echo "❌ Réponse non-JSON : ${resp:0:200}" >&2
    return 1
  fi
  echo "$resp" | python3 -m json.tool
}

assign_role() {
  local user_id="$1" role_id="$2" label="$3"
  echo "→ Rôle $label pour $user_id"
  local resp
  resp=$(curl -s -X POST "$KERNEL/api/administration/users/$user_id/roles" "${H[@]}" \
    -d "{\"roleId\":\"$role_id\",\"scopeType\":\"TENANT\",\"scopeId\":\"$FLEET_TENANT_ID\",\"scope\":\"TENANT\"}")
  echo "$resp" | python3 -c "
import sys,json
d=json.load(sys.stdin)
ok=d.get('success')
msg=d.get('message','')
print(' ', ok, msg[:70])
if not ok and 'already assigned' not in msg.lower():
    sys.exit(1)
" || true
}

link_actor() {
  local email="$1" first="$2" last="$3"
  echo "→ Acteur org pour $email"
  local resp actor_id
  resp=$(curl -s -X POST "$KERNEL/api/actors" "${H[@]}" \
    -d "{\"organizationId\":\"$FLEET_ORG_ID\",\"firstName\":\"$first\",\"lastName\":\"$last\",\"email\":\"$email\",\"type\":\"EMPLOYEE\"}")
  actor_id=$(echo "$resp" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('id') or '')" 2>/dev/null || echo "")
  if [ -z "$actor_id" ]; then
    echo "  ⚠️  Création acteur ignorée ($(echo "$resp" | python3 -c "import sys,json; print(json.load(sys.stdin).get('message','')[:60])" 2>/dev/null || echo 'erreur'))"
    return 0
  fi
  curl -s -X POST "$KERNEL/api/organizations/$FLEET_ORG_ID/actors" "${H[@]}" \
    -d "{\"actorId\":\"$actor_id\",\"type\":\"EMPLOYEE\"}" \
    | python3 -c "import sys,json; d=json.load(sys.stdin); print('  link:', d.get('success'), d.get('message','')[:60])"
}

echo ""
echo "=== Services org ==="
SERVICES=$(curl -s "$KERNEL/api/organizations/$FLEET_ORG_ID/services" "${H[@]}")
kernel_json "$SERVICES" | head -25

assign_role "$MANAGER1_ID" "$ROLE_MANAGER_ID" "FLEET_MANAGER"
assign_role "$DRIVER_ID" "$ROLE_DRIVER_ID" "FLEET_DRIVER"

link_actor "manager1@fleetman.cm" "Jean" "Dupont"
link_actor "driver@fleetman.cm" "André" "Mbarga"

echo ""
echo "=== Acteurs org ==="
ACTORS=$(curl -s "$KERNEL/api/organizations/$FLEET_ORG_ID/actors" "${H[@]}")
kernel_json "$ACTORS"

echo ""
echo "✅ Provision Kernel terminée"
