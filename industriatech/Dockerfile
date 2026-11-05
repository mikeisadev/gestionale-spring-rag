# ============================================================
# Backend Spring Boot - build multi-stage
# Stage 1 (build): compila il jar con Maven e il JDK completo
# Stage 2 (run):   esegue il jar con il solo JRE -> immagine più leggera
# ============================================================

# ---------- 1. BUILD ----------
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

# Prima copiamo SOLO il pom e scarichiamo le dipendenze.
# Docker salva questo passaggio in cache: finché il pom non cambia,
# le dipendenze non vengono riscaricate a ogni build.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

# Poi copiamo il codice e creiamo il jar (i test si lanciano a parte)
COPY src ./src
RUN mvn -q -B -DskipTests package

# ---------- 2. RUN ----------
FROM eclipse-temurin:25-jre
WORKDIR /app

# Non eseguiamo l'applicazione come root: è una buona pratica di sicurezza
RUN groupadd --system spring && useradd --system --gid spring spring \
    && mkdir -p /app/documents/pdf && chown -R spring:spring /app
USER spring

COPY --from=build /build/target/*.jar app.jar

EXPOSE 8081

# MaxRAMPercentage: la JVM usa al massimo il 75% della memoria assegnata al container
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
