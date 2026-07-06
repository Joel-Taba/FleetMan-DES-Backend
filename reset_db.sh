#!/bin/bash
# Recrée une base PostgreSQL propre pour FleetMan (Docker).
# Usage : ./reset_db.sh
# Efface toutes les données du volume Docker — réservé au développement local.

set -e
cd "$(dirname "$0")"

echo "🗑️  Arrêt des conteneurs et suppression du volume PostgreSQL..."
docker compose down -v

echo "🐳 Redémarrage PostgreSQL + Redis + Kafka..."
docker compose up -d

echo "⏳ Attente que PostgreSQL soit prêt (fleetmanBD sur localhost:5433)..."
for i in $(seq 1 30); do
  if docker exec fleet-management-db pg_isready -U fleet_admin -d fleetmanBD >/dev/null 2>&1; then
echo "✅ PostgreSQL prêt."
echo ""
echo "Prochaines étapes :"
echo "  1. Démarrer le backend (migrations Liquibase + seed demo) :"
echo "     SPRING_PROFILES_ACTIVE=local,demo ./mvnw spring-boot:run"
echo "  2. Provisionner le Kernel (optionnel, idempotent) :"
echo "     ./kernel_provision_users.sh"
exit 0
  fi
  sleep 1
done

echo "❌ Timeout — vérifiez : docker compose logs fleet-db"
exit 1
