# ── Build stage ──────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Cache dependencies separately so code-only changes don't re-download the world
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# ── Runtime stage ────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY --from=build /app/target/*.war app.war

# Render (and most PaaS) inject PORT at runtime; application.properties
# already reads it via ${PORT:8080}.
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.war"]
