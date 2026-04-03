# ============================================================
# Multi-stage Dockerfile (Production Ready)
# ============================================================

# ---- Stage 1: Build ----
FROM maven:3.9.5-eclipse-temurin-17 AS builder

WORKDIR /build

# Copy pom.xml first (better caching)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build JAR (skip tests — run in Jenkins)
RUN mvn clean package -DskipTests -B


# ---- Stage 2: Runtime ----
FROM eclipse-temurin:17-jre-alpine

# Create non-root user (security best practice)
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy JAR from builder
COPY --from=builder /build/target/*.jar app.jar

# Create logs directory
RUN mkdir -p /app/logs && chown -R appuser:appgroup /app

USER appuser

# Expose port
EXPOSE 9090

# JVM tuning for container

# Only ONE Entrypoint is allowed
#ENTRYPOINT ["java",
#  "-XX:+UseContainerSupport",
#  "-XX:MaxRAMPercentage=75.0",
#  "-Djava.security.egd=file:/dev/./urandom",
#  "-jar", "app.jar"
#]


ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar" \
]