# ---- Stage 1: Build ----
FROM maven:3.9.5-eclipse-temurin-17 AS builder

WORKDIR /build

# ✅ FIX 1: Force IPv4 (CRITICAL)
ENV MAVEN_OPTS="-Djava.net.preferIPv4Stack=true"

# ✅ FIX 2: Copy pom.xml first (cache layer)
COPY pom.xml .

# ✅ FIX 3: Add retry + no-transfer-progress
RUN mvn -B -ntp dependency:go-offline || \
    mvn -B -ntp dependency:go-offline

# Copy source
COPY src ./src

# ✅ FIX 4: Build with same IPv4 + optimized flags
RUN mvn -B -ntp clean package -DskipTests


# ---- Stage 2: Runtime ----
FROM eclipse-temurin:17-jre-alpine

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=builder /build/target/*.jar app.jar

RUN mkdir -p /app/logs && chown -R appuser:appgroup /app

USER appuser

EXPOSE 9090

ENTRYPOINT ["java",
  "-XX:+UseContainerSupport",
  "-XX:MaxRAMPercentage=75.0",
  "-Djava.net.preferIPv4Stack=true",
  "-Djava.security.egd=file:/dev/./urandom",
  "-jar", "app.jar"
]