# 🗄️ Database Setup Guide (Local Development)

This guide explains how to set up and initialize the Fleet Management database on your local machine. This process is fully automated for both **Linux** and **Windows**.

## 📋 Prerequisites

Ensure you have the following installed:
- **Docker** & **Docker Compose**
  - *Linux*: `sudo apt install docker-compose`
  - *Windows*: Install [Docker Desktop](https://www.docker.com/products/docker-desktop/)

## 🚀 Step 1: Start the Database Container

Open a terminal at the root of the project and run:

```bash
# Start the PostgreSQL 16 container in the background
docker-compose up -d
```

*Note: This will automatically pull the official Postgres 16 image and create a database named `yowyob_db`.*

## ⚙️ Step 2: Configure the Application

The application is configured to use a **Hybrid Mode** (Local DB + Remote Kafka/Redis).

1. Open `src/main/resources/application.yml`.
2. Ensure the active profile is set to `local`:
   ```yaml
   spring:
     profiles:
       active: local
   ```
3. (Optional) In the `LOCAL CONFIGURATION` block, verify that the credentials match those in `docker-compose.yml` (default: `fleet_admin` / `fleet_password`).

## 🛠️ Step 3: Run and Auto-Initialize

Simply start the application. The system will detect the local environment and automatically:
1. Create all tables and ENUM types (from `resources/local/schema.sql`).
2. Inject 100 test users and sample data (from `resources/local/data.sql`).

**Run Command:**
```bash
# Linux / macOS
./mvnw clean spring-boot:run

# Windows (CMD / PowerShell)
mvnw.cmd clean spring-boot:run
```

## ✅ Step 4: Verify the Setup

Once the log shows `🚀 LOCAL DATABASE READY!`, open your browser:

- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **Check Data**: Find the `health-check-controller` and execute `/api/v1/health/users-count`. You should see `users_in_db: 100`.

## ❌ Troubleshooting

- **Port 5432 already in use**: If you have a local PostgreSQL installed outside of Docker, stop it or change the port mapping in `docker-compose.yml`.
- **DNS/Pull Issues**: If Docker fails to pull the image, try:
  ```bash
  docker pull postgres:16
  ```
