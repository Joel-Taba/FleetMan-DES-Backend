# --- Stage 1: Build ---
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Copie du pom.xml et téléchargement des dépendances (mise en cache des layers)
COPY pom.xml .
RUN mvn dependency:go-offline

# Copie du code source et compilation
COPY src ./src
RUN mvn clean package -DskipTests

# --- Stage 2: Run ---
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Création d'un utilisateur non-root pour la sécurité
RUN addgroup yowyob && adduser yowyob --ingroup yowyob
USER yowyob:yowyob

# Copie du JAR depuis l'étape de build
COPY --from=builder /app/target/*.jar app.jar

# Variables d'environnement par défaut (peuvent être surchargées par docker-compose)
ENV SPRING_PROFILES_ACTIVE=prod

# Exposition du port
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
