#!/bin/bash
echo "🧹 Nettoyage complet de la base de données..."
docker-compose down -v
echo "✅ Base de données supprimée. Relancez ./run_local.sh pour recréer proprement."