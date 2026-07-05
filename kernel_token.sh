#!/usr/bin/env bash
# =============================================================================
# kernel_token.sh — Gestion automatique du token Kernel pour les appels curl
# =============================================================================
# Usage :
#   source ./kernel_token.sh          → exporte TOKEN + OWNER_TOKEN dans le shell courant
#   ./kernel_token.sh                 → affiche les tokens (sans export dans le shell parent)
#
# Le login Kernel se fait en 2 étapes (flow discover-contexts → select-context).
# Le script vérifie si le token sauvegardé est encore valide en décodant le claim
# 'exp' du JWT. S'il est expiré ou absent, il refait un login automatiquement.
# =============================================================================

# ----------------------------------------------------------------------------- 
# CONFIGURATION
# -----------------------------------------------------------------------------
readonly KERNEL_URL="https://kernel-core.yowyob.com"
readonly CLIENT_ID="fleet-management-backend"
readonly API_KEY="K5mWlNhc6zH2-JcaZJWSs1sLxUzjS902u4MxEnJar5g"
readonly TENANT_ID="f5b814d9-766e-4c87-91ec-d6c8e32cb56c"
readonly ORG_ID="5e69f5c5-1f03-41cb-a4a3-d59188f73323"
readonly SERVICE_NAME="FLEET_MANAGEMENT"

# Compte Owner (admin Kernel)
readonly OWNER_EMAIL="joeltaba4@gmail.com"
readonly OWNER_PASSWORD="FleetMan2026!"

# Compte manager1 (tests FleetMan)
readonly MANAGER_EMAIL="manager1@fleetman.cm"
readonly MANAGER_PASSWORD="FleetMan2026!"

# Fichiers de cache dans /tmp (réinitialisés au reboot)
readonly OWNER_TOKEN_FILE="/tmp/kernel_owner_token.json"
readonly MANAGER_TOKEN_FILE="/tmp/kernel_manager_token.json"

# Marge de sécurité : renouvelle si moins de 120s restantes
readonly MARGIN_SECONDS=120

# -----------------------------------------------------------------------------
# COULEURS
# -----------------------------------------------------------------------------
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; CYAN='\033[0;36m'; NC='\033[0m'

log_info()    { echo -e "${BLUE}ℹ️  $*${NC}" >&2; }
log_success() { echo -e "${GREEN}✅ $*${NC}" >&2; }
log_warn()    { echo -e "${YELLOW}⚠️  $*${NC}" >&2; }
log_error()   { echo -e "${RED}❌ $*${NC}" >&2; }

# -----------------------------------------------------------------------------
# Décode le claim 'exp' depuis un JWT et retourne l'unix timestamp
# -----------------------------------------------------------------------------
get_token_expiry() {
    local token="$1"
    # Extraire le payload (2ème segment), ajouter padding base64 si nécessaire
    echo "$token" \
        | cut -d. -f2 \
        | awk '{ n = length($0) % 4; if (n == 2) print $0 "=="; else if (n == 3) print $0 "="; else print $0 }' \
        | tr '_-' '/+' \
        | base64 -d 2>/dev/null \
        | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('exp', 0))" 2>/dev/null \
        || echo "0"
}

# Retourne 0 si le token est valide (avec marge), 1 sinon
is_token_valid() {
    local token="$1"
    local label="${2:-TOKEN}"
    local exp now remaining

    exp=$(get_token_expiry "$token")
    now=$(date +%s)
    remaining=$(( exp - now ))

    if [ "$remaining" -gt "$MARGIN_SECONDS" ]; then
        log_info "[$label] Token valide — encore ${remaining}s ($(( remaining / 60 ))m $(( remaining % 60 ))s)"
        return 0
    else
        if [ "$remaining" -le 0 ]; then
            log_warn "[$label] Token expiré depuis $(( -remaining ))s"
        else
            log_warn "[$label] Token expire dans ${remaining}s < marge ${MARGIN_SECONDS}s — renouvellement"
        fi
        return 1
    fi
}

# -----------------------------------------------------------------------------
# Login Kernel en 2 étapes : discover-contexts → select-context
# Sauvegarde la réponse complète dans $cache_file
# Retourne le accessToken sur stdout, ou vide si erreur
# -----------------------------------------------------------------------------
do_kernel_login() {
    local principal="$1"
    local password="$2"
    local cache_file="$3"
    local label="${4:-LOGIN}"

    log_info "[$label] Étape 1/2 — discover-contexts pour : $principal"

    # ── Étape 1 : discover-contexts ──────────────────────────────────────────
    local discover_resp
    discover_resp=$(curl -s -X POST "${KERNEL_URL}/api/auth/discover-contexts" \
        -H "Content-Type: application/json" \
        -H "X-Client-Id: ${CLIENT_ID}" \
        -H "X-Api-Key: ${API_KEY}" \
        -H "X-Tenant-Id: ${TENANT_ID}" \
        -d "{\"principal\":\"${principal}\",\"password\":\"${password}\"}" \
        2>/dev/null)

    if [ -z "$discover_resp" ]; then
        log_error "[$label] Réponse vide — problème réseau ou Kernel inaccessible"
        return 1
    fi

    local discover_success
    discover_success=$(echo "$discover_resp" | python3 -c \
        "import sys,json; d=json.load(sys.stdin); print(d.get('success', False))" 2>/dev/null)

    if [ "$discover_success" != "True" ]; then
        local msg
        msg=$(echo "$discover_resp" | python3 -c \
            "import sys,json; d=json.load(sys.stdin); print(d.get('message', 'Erreur inconnue'))" 2>/dev/null)
        log_error "[$label] discover-contexts échoué : $msg"
        return 1
    fi

    # Extraire selectionToken et contextId (premier contexte avec FLEET_MANAGEMENT ou le premier disponible)
    local selection_token context_id org_id
    selection_token=$(echo "$discover_resp" | python3 -c \
        "import sys,json; d=json.load(sys.stdin); print(d['data']['selectionToken'])" 2>/dev/null)

    context_id=$(echo "$discover_resp" | python3 -c "
import sys, json
d = json.load(sys.stdin)['data']
contexts = d.get('contexts', [])
# Préférer le contexte avec FLEET_MANAGEMENT
ctx = next(
    (c for c in contexts if any(
        o.get('service','').upper() == 'FLEET_MANAGEMENT'
        for o in (c.get('organizations') or [])
    )),
    contexts[0] if contexts else None
)
print(ctx['contextId'] if ctx else '')
" 2>/dev/null)

    org_id=$(echo "$discover_resp" | python3 -c "
import sys, json
d = json.load(sys.stdin)['data']
contexts = d.get('contexts', [])
ctx = next(
    (c for c in contexts if any(
        o.get('service','').upper() == 'FLEET_MANAGEMENT'
        for o in (c.get('organizations') or [])
    )),
    contexts[0] if contexts else None
)
if ctx:
    orgs = ctx.get('organizations') or []
    fleet_org = next((o for o in orgs if o.get('service','').upper() == 'FLEET_MANAGEMENT'), None)
    print(fleet_org['organizationId'] if fleet_org else (orgs[0]['organizationId'] if orgs else 'null'))
else:
    print('null')
" 2>/dev/null)

    if [ -z "$selection_token" ] || [ -z "$context_id" ]; then
        log_error "[$label] Impossible d'extraire selectionToken ou contextId"
        return 1
    fi

    log_info "[$label] Étape 2/2 — select-context (contextId=${context_id:0:8}...)"

    # ── Étape 2 : select-context ─────────────────────────────────────────────
    local select_body
    if [ "$org_id" = "null" ] || [ -z "$org_id" ]; then
        select_body="{\"selectionToken\":\"${selection_token}\",\"contextId\":\"${context_id}\",\"organizationId\":null}"
    else
        select_body="{\"selectionToken\":\"${selection_token}\",\"contextId\":\"${context_id}\",\"organizationId\":\"${org_id}\"}"
    fi

    local select_resp
    select_resp=$(curl -s -X POST "${KERNEL_URL}/api/auth/select-context" \
        -H "Content-Type: application/json" \
        -H "X-Client-Id: ${CLIENT_ID}" \
        -H "X-Api-Key: ${API_KEY}" \
        -H "X-Tenant-Id: ${TENANT_ID}" \
        -d "$select_body" \
        2>/dev/null)

    if [ -z "$select_resp" ]; then
        log_error "[$label] Réponse vide à select-context"
        return 1
    fi

    local select_success
    select_success=$(echo "$select_resp" | python3 -c \
        "import sys,json; d=json.load(sys.stdin); print(d.get('success', False))" 2>/dev/null)

    if [ "$select_success" != "True" ]; then
        local msg
        msg=$(echo "$select_resp" | python3 -c \
            "import sys,json; d=json.load(sys.stdin); print(d.get('message', 'Erreur inconnue'))" 2>/dev/null)
        log_error "[$label] select-context échoué : $msg"
        return 1
    fi

    # Sauvegarder la réponse complète
    echo "$select_resp" > "$cache_file"

    # Extraire et retourner le token
    local token
    token=$(echo "$select_resp" | python3 -c \
        "import sys,json; d=json.load(sys.stdin); print(d['data']['session']['accessToken'])" 2>/dev/null)

    if [ -z "$token" ]; then
        log_error "[$label] accessToken introuvable dans la réponse"
        return 1
    fi

    local expiry exp_human
    expiry=$(get_token_expiry "$token")
    exp_human=$(date -d "@$expiry" '+%H:%M:%S' 2>/dev/null \
             || date -r "$expiry" '+%H:%M:%S' 2>/dev/null \
             || echo "N/A")
    log_success "[$label] Login réussi — token expire à $exp_human"

    echo "$token"
}

# -----------------------------------------------------------------------------
# Obtient un token valide : cache ou nouveau login
# -----------------------------------------------------------------------------
get_valid_token() {
    local principal="$1"
    local password="$2"
    local cache_file="$3"
    local label="$4"

    local token=""

    # Essayer de charger depuis le cache
    if [ -f "$cache_file" ]; then
        token=$(python3 -c \
            "import json; d=json.load(open('$cache_file')); print(d['data']['session']['accessToken'])" \
            2>/dev/null || echo "")
    fi

    # Vérifier la validité du token en cache
    if [ -n "$token" ] && is_token_valid "$token" "$label"; then
        log_success "[$label] Token en cache réutilisé"
        echo "$token"
        return 0
    fi

    # Token absent ou expiré — nouveau login
    log_info "[$label] Nouveau login nécessaire..."
    token=$(do_kernel_login "$principal" "$password" "$cache_file" "$label")

    if [ -z "$token" ]; then
        log_error "[$label] Impossible d'obtenir un token valide"
        return 1
    fi

    echo "$token"
}

# -----------------------------------------------------------------------------
# PROGRAMME PRINCIPAL
# -----------------------------------------------------------------------------
main() {
    echo ""
    echo -e "${CYAN}========================================================"
    echo " kernel_token.sh — Tokens Kernel automatiques"
    echo -e "========================================================${NC}"
    echo ""

    # ── Token Owner ──────────────────────────────────────────────────────────
    log_info "=== Token OWNER (${OWNER_EMAIL}) ==="
    OWNER_TOKEN=$(get_valid_token \
        "$OWNER_EMAIL" "$OWNER_PASSWORD" "$OWNER_TOKEN_FILE" "OWNER") || OWNER_TOKEN=""

    echo ""

    # ── Token Manager1 ───────────────────────────────────────────────────────
    log_info "=== Token MANAGER1 (${MANAGER_EMAIL}) ==="
    MANAGER_TOKEN=$(get_valid_token \
        "$MANAGER_EMAIL" "$MANAGER_PASSWORD" "$MANAGER_TOKEN_FILE" "MANAGER1") || MANAGER_TOKEN=""

    echo ""

    # ── Résumé et export ─────────────────────────────────────────────────────
    echo -e "${CYAN}========================================================"
    echo " Variables exportées"
    echo -e "========================================================${NC}"

    if [ -n "$OWNER_TOKEN" ]; then
        export OWNER_TOKEN
        echo "  OWNER_TOKEN   = ${OWNER_TOKEN:0:40}..."
        log_success "OWNER_TOKEN exporté"
    else
        log_warn "OWNER_TOKEN non disponible"
    fi

    if [ -n "$MANAGER_TOKEN" ]; then
        export TOKEN="$MANAGER_TOKEN"
        export MANAGER_TOKEN
        echo "  TOKEN         = ${TOKEN:0:40}..."
        echo "  MANAGER_TOKEN = ${MANAGER_TOKEN:0:40}..."
        log_success "TOKEN + MANAGER_TOKEN exportés"
    else
        log_warn "TOKEN (manager1) non disponible"
    fi

    # Variables d'infra toujours utiles
    export KERNEL="$KERNEL_URL"
    export CID="$CLIENT_ID"
    export KEY="$API_KEY"
    export FLEET_TENANT_ID="$TENANT_ID"
    export FLEET_ORG_ID="$ORG_ID"

    echo ""
    echo "  KERNEL          = $KERNEL"
    echo "  CID             = $CID"
    echo "  FLEET_TENANT_ID = $FLEET_TENANT_ID"
    echo "  FLEET_ORG_ID    = $FLEET_ORG_ID"
    echo ""

    log_success "Prêt. Utilisation : source ./kernel_token.sh"
    echo ""
    echo -e "${CYAN}Exemple d'utilisation :${NC}"
    echo '  curl -s -H "Authorization: Bearer $TOKEN" \'
    echo '    http://localhost:8081/api/v1/vehicles | python3 -m json.tool'
    echo ""
}

main "$@"
