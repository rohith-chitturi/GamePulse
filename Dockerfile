# syntax=docker/dockerfile:1

# ==============================================================================
# Stage 1: Build Application Artifact
# ==============================================================================
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /workspace

# Leverage Docker layer caching for Maven dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copy source tree and compile executable application jar
COPY src ./src
RUN mvn clean package -DskipTests -B

# ==============================================================================
# Stage 2: Minimal Hardened Production Runtime
# ==============================================================================
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

# Security: Non-root system group and user
RUN addgroup -S gamepulse && adduser -S gamepulse -G gamepulse

# Install curl for container health check probe
RUN apk add --no-cache curl

# Copy only the compiled fat jar from the builder stage
COPY --from=builder --chown=gamepulse:gamepulse /workspace/target/gamepulse-*.jar app.jar

# Container-aware JVM memory sizing and entropy optimization
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -Djava.security.egd=file:/dev/./urandom"

USER gamepulse:gamepulse

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

