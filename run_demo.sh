#!/bin/bash
# Démarre le backend en mode demo (auth fake + données de test).
# Voir IDENTIFIANTS_TEST.md à la racine du dépôt.

echo "🚀 Démarrage Fleet Management — profil DEMO (auth fake + seed)..."

docker compose up -d
echo "⏳ Attente PostgreSQL (5s)..."
sleep 5

echo "☕ Spring Boot (profiles: local,demo)..."
./mvnw clean spring-boot:run -Dspring-boot.run.profiles=local,demo 2>&1 | tee /tmp/fleet-demo-logs.txt
