#!/usr/bin/env bash
# =============================================================================
# kernel_token_daemon.sh — Renouvelle automatiquement les tokens Kernel
# =============================================================================
# Usage :
#   ./scripts/kernel_token_daemon.sh          # boucle toutes les 14 min
#   ./scripts/kernel_token_daemon.sh 600      # boucle toutes les 10 min
#
# Lance en arrière-plan :
#   nohup ./scripts/kernel_token_daemon.sh >> /tmp/kernel-token-daemon.log 2>&1 &
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INTERVAL="${1:-840}"

exec bash "$SCRIPT_DIR/../kernel_token.sh" --daemon "$INTERVAL"
