#!/bin/bash

echo "🚀 Démarrage de l'environnement Fleet Management (LOCAL)..."

# 1. Démarrer la Base de Données
echo "🐳 Lancement du conteneur PostgreSQL..."
docker  compose up -d

# 2. Attente de sécurité (pour que Postgres soit prêt à accepter la connexion JDBC de Liquibase)
echo "⏳ Attente de l'initialisation de la base de données (5s)..."
sleep 5

# 3. Lancer l'application Spring Boot
echo "☕ Lancement de l'application..."
echo "📋 Les logs sont capturés dans /tmp/fleet-logs.txt"
./mvnw clean spring-boot:run -Dspring.profiles.active=local 2>&1 | tee /tmp/fleet-logs.txt