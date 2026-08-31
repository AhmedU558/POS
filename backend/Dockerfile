# syntax=docker/dockerfile:1

# Build stage -----------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependency layer: cached until pom.xml changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# Runtime stage ---------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Never run the API as root (SAD section 30). wget is used by the compose healthcheck.
RUN apk add --no-cache wget \
    && addgroup -S pos && adduser -S pos -G pos
WORKDIR /app

COPY --from=build /build/target/*.jar app.jar
RUN chown -R pos:pos /app
USER pos

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
