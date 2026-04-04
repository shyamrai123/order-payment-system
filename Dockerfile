# ---- Stage 1: Build ----
FROM maven:3.9.5-eclipse-temurin-17 AS builder

WORKDIR /build

ENV MAVEN_OPTS="-Djava.net.preferIPv4Stack=true"

COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline || mvn -B -ntp dependency:go-offline

COPY src ./src

RUN mvn -B -ntp clean package -DskipTests


# ---- Stage 2: Runtime ----
FROM eclipse-temurin:17-jre-alpine

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=builder /build/target/*.jar app.jar

RUN mkdir -p /app/logs && chown -R appuser:appgroup /app

USER appuser

EXPOSE 9090

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-Djava.net.preferIPv4Stack=true", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]