# ═══════════════════════════════════════════════════════════════════════════
# Atruvia Stablecoin Integration Platform — Single-Container Railway Deploy
# Multi-Stage Build: Angular → injiziert in Spring Boot → schlankes JRE Image
# ═══════════════════════════════════════════════════════════════════════════

# ── STAGE 1: Angular Frontend kompilieren ────────────────────────────────────
FROM node:20-alpine AS frontend-build
WORKDIR /app/frontend

COPY frontend/package*.json ./
RUN npm ci --quiet

COPY frontend/ ./
# Production-Build: Tree-Shaking, Minification, kein Source-Map
# Angular 18 (application-builder) legt Browser-Dateien in dist/browser/ ab
RUN npx ng build --configuration production --output-path=dist

# ── STAGE 2: Spring Boot Backend + statisches Frontend zusammenführen ─────────
FROM maven:3.9-eclipse-temurin-21-alpine AS backend-build
WORKDIR /app/backend

# Angular-Output (browser/) als Spring Boot Static Resources injizieren.
# Spring Boot serviert /static/** automatisch unter / — kein Nginx nötig.
COPY --from=frontend-build /app/frontend/dist/browser/ \
    /app/backend/src/main/resources/static/

COPY backend/pom.xml ./
# Dependency-Cache-Layer (wird nur neu geladen wenn pom.xml sich ändert)
RUN mvn dependency:go-offline -q

COPY backend/src ./src
RUN mvn clean package -DskipTests -q

# ── STAGE 3: Minimales JRE-Runtime-Image (< 200 MB) ──────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=backend-build /app/backend/target/stablecoin-backend-1.0.0.jar app.jar

# JVM-Tuning für Kleinstinstanzen (512 MB RAM Max, Shared CPU)
# MaxRAMPercentage=70 → JVM nutzt max. 70% des verfügbaren (~357 MB)
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=70.0 -XX:ActiveProcessorCount=1 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

# sh -c expandiert $JAVA_OPTS korrekt (in JSON-Array-Form würde es nicht expandiert)
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
