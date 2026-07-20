#!/usr/bin/env bash
set -euo pipefail

KERNEL_URL="${KERNEL_URL:-https://kernel-core.yowyob.com}"
CLIENT_ID="${CLIENT_ID:-fleet-management-backend}"
API_KEY="${API_KEY:-K5mWlNhc6zH2-JcaZJWSs1sLxUzjS902u4MxEnJar5g}"
TENANT_ID="${TENANT_ID:-f5b814d9-766e-4c87-91ec-d6c8e32cb56c}"
ORG_ID="${ORG_ID:-5e69f5c5-1f03-41cb-a4a3-d59188f73323}"
OWNER_EMAIL="${OWNER_EMAIL:-joeltaba4@gmail.com}"
OWNER_PASSWORD="${OWNER_PASSWORD:-FleetMan2026!}"
FAKE_FILE_ID="00000000-0000-4000-8000-000000000001"
FAKE_RESOURCE_ID="00000000-0000-4000-8000-000000000002"

probe() {
  local service="$1"
  local method="$2"
  local path="$3"
  local need_auth="${4:-yes}"
  local extra_headers="${5:-}"

  local headers=(
    -H "X-Client-Id: ${CLIENT_ID}"
    -H "X-Api-Key: ${API_KEY}"
    -H "X-Tenant-Id: ${TENANT_ID}"
    -H "X-Organization-Id: ${ORG_ID}"
  )

  if [[ "$need_auth" == "yes" && -n "${TOKEN:-}" ]]; then
    headers+=(-H "Authorization: Bearer ${TOKEN}")
  fi

  if [[ -n "$extra_headers" ]]; then
    # shellcheck disable=SC2206
    headers+=($extra_headers)
  fi

  local body http_code
  body=$(curl -sS --max-time 25 -X "$method" "${headers[@]}" "${KERNEL_URL}${path}" -w "\n__HTTP__%{http_code}" 2>&1 || true)
  http_code=$(echo "$body" | sed -n 's/.*__HTTP__//p' | tail -1)
  body=$(echo "$body" | sed 's/__HTTP__.*//')

  local success message error_code verdict
  success=$(echo "$body" | python3 -c "import sys,json
try:
 d=json.load(sys.stdin)
 print(d.get('success',''))
except Exception:
 print('')" 2>/dev/null || echo "")
  message=$(echo "$body" | python3 -c "import sys,json
try:
 d=json.load(sys.stdin)
 print((d.get('message') or d.get('error') or '')[:180])
except Exception:
 print('')" 2>/dev/null || echo "")
  error_code=$(echo "$body" | python3 -c "import sys,json
try:
 d=json.load(sys.stdin)
 print(d.get('errorCode',''))
except Exception:
 print('')" 2>/dev/null || echo "")

  if [[ "$http_code" == "200" && "$success" == "True" ]]; then
    verdict="OK"
  elif [[ "$http_code" == "200" ]]; then
    verdict="HTTP200"
  elif [[ "$http_code" == "401" ]]; then
    verdict="UNAUTHORIZED"
  elif [[ "$http_code" == "403" ]]; then
    verdict="FORBIDDEN"
  elif [[ "$http_code" == "404" ]]; then
    verdict="NOT_FOUND"
  else
    verdict="ERROR_${http_code}"
  fi

  printf '%s|%s|%s|%s|%s|%s|%s\n' "$service" "$method $path" "$http_code" "$verdict" "$success" "$error_code" "$message"
}

echo "=== KERNEL ACCESS PROBE ==="
echo "kernel=${KERNEL_URL}"
echo "client=${CLIENT_ID}"
echo "tenant=${TENANT_ID}"
echo "org=${ORG_ID}"
echo ""

echo "--- AUTH: client credentials (discover-contexts) ---"
probe "AUTH" POST "/api/auth/discover-contexts" no \
  -H "Content-Type: application/json" \
  --data "{\"principal\":\"${OWNER_EMAIL}\",\"password\":\"${OWNER_PASSWORD}\"}" 2>/dev/null || true

# Fix probe for POST with body - need different approach
auth_resp=$(curl -sS --max-time 25 -X POST "${KERNEL_URL}/api/auth/discover-contexts" \
  -H "Content-Type: application/json" \
  -H "X-Client-Id: ${CLIENT_ID}" \
  -H "X-Api-Key: ${API_KEY}" \
  -H "X-Tenant-Id: ${TENANT_ID}" \
  -d "{\"principal\":\"${OWNER_EMAIL}\",\"password\":\"${OWNER_PASSWORD}\"}" \
  -w "\n__HTTP__%{http_code}" 2>&1 || true)
auth_http=$(echo "$auth_resp" | sed -n 's/.*__HTTP__//p' | tail -1)
auth_body=$(echo "$auth_resp" | sed 's/__HTTP__.*//')
auth_success=$(echo "$auth_body" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('success',False))" 2>/dev/null || echo "False")
auth_msg=$(echo "$auth_body" | python3 -c "import sys,json; d=json.load(sys.stdin); print((d.get('message') or '')[:180])" 2>/dev/null || echo "")

if [[ "$auth_success" != "True" ]]; then
  echo "AUTH|discover-contexts|${auth_http}|FAILED|${auth_success}||${auth_msg}"
  echo ""
  echo "Cannot continue without token."
  exit 1
fi

selection_token=$(echo "$auth_body" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['selectionToken'])" 2>/dev/null)
context_id=$(echo "$auth_body" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
contexts=d.get('contexts',[])
ctx=next((c for c in contexts if any(o.get('service','').upper() in ('FLEET_MANAGEMENT','COMMERCIAL') for o in (c.get('organizations') or []))), contexts[0] if contexts else None)
print(ctx['contextId'] if ctx else '')
" 2>/dev/null)
sel_org=$(echo "$auth_body" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
contexts=d.get('contexts',[])
ctx=next((c for c in contexts if any(o.get('service','').upper() in ('FLEET_MANAGEMENT','COMMERCIAL') for o in (c.get('organizations') or []))), contexts[0] if contexts else None)
if not ctx: print('null'); raise SystemExit
orgs=ctx.get('organizations') or []
o=next((x for x in orgs if x.get('service','').upper() in ('FLEET_MANAGEMENT','COMMERCIAL')), orgs[0] if orgs else None)
print(o['organizationId'] if o else 'null')
" 2>/dev/null)

select_body="{\"selectionToken\":\"${selection_token}\",\"contextId\":\"${context_id}\",\"organizationId\":${sel_org:+\"$sel_org\"}${sel_org:-null}}"
select_resp=$(curl -sS --max-time 25 -X POST "${KERNEL_URL}/api/auth/select-context" \
  -H "Content-Type: application/json" \
  -H "X-Client-Id: ${CLIENT_ID}" \
  -H "X-Api-Key: ${API_KEY}" \
  -H "X-Tenant-Id: ${TENANT_ID}" \
  -d "$select_body" \
  -w "\n__HTTP__%{http_code}" 2>&1 || true)
select_http=$(echo "$select_resp" | sed -n 's/.*__HTTP__//p' | tail -1)
select_body_json=$(echo "$select_resp" | sed 's/__HTTP__.*//')
TOKEN=$(echo "$select_body_json" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['data']['session']['accessToken'])" 2>/dev/null || echo "")
select_success=$(echo "$select_body_json" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('success',False))" 2>/dev/null || echo "False")

echo "AUTH|discover-contexts|${auth_http}|OK|${auth_success}||contexts found"
echo "AUTH|select-context|${select_http}|$([[ \"$select_success\" == \"True\" ]] && echo OK || echo FAILED)|${select_success}||token_len=${#TOKEN}"

if [[ -z "$TOKEN" ]]; then
  echo "Token acquisition failed."
  exit 1
fi

echo ""
echo "--- SERVICE PROBES (authenticated) ---"
printf 'SERVICE|ENDPOINT|HTTP|VERDICT|SUCCESS|ERROR_CODE|MESSAGE\n'

while IFS= read -r line; do
  echo "$line"
done < <(
  probe "AUTH" GET "/api/users/me"
  probe "ORGANIZATION" GET "/api/organizations/${ORG_ID}"
  probe "RESOURCE" GET "/api/resources/search?organizationId=${ORG_ID}&q=test&size=1"
  probe "FILE" GET "/api/files/${FAKE_FILE_ID}/metadata"
  probe "ACTOR" GET "/api/organizations/${ORG_ID}/actors"
  probe "ACTOR" GET "/api/actors/me"
  probe "ADMINISTRATION" GET "/api/administration/roles"
  probe "ADMINISTRATION" GET "/api/administration/governance/organizations/${ORG_ID}"
  probe "COMMERCIAL" GET "/api/organizations/commercial-subscriptions/catalog"
  probe "COMMERCIAL" GET "/api/organizations/${ORG_ID}/commercial-subscriptions"
  probe "KYC" GET "/api/document-governance/organizations/${ORG_ID}/overview"
  probe "KYC" GET "/api/document-hub/organizations/${ORG_ID}/overview"
  probe "KYC" GET "/api/document-governance/targets/FLEET_VEHICLE/${FAKE_RESOURCE_ID}"
)

echo ""
echo "--- KAFKA DNS (local) ---"
getent hosts kafka 2>&1 || echo "kafka: not resolvable locally"
