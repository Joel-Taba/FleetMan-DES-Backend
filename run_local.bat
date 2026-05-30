@echo off
echo 🚀 Demarrage de l'environnement Fleet Management (LOCAL)...

REM 1. Démarrer la Base de Données
echo 🐳 Lancement du conteneur PostgreSQL...
docker-compose up -d

REM 2. Attente de sécurité
echo ⏳ Attente de l'initialisation de la base de données (5s)...
timeout /t 5 /nobreak

REM 3. Lancer l'application Spring Boot
echo ☕ Lancement de l'application...
call mvnw.cmd clean spring-boot:run -Dspring.profiles.active=local

pause