# syntax=docker/dockerfile:1.7
#
# Spring Boot backend — multi-stage build.
#
#   docker build -t schulkasse-backend backend/
#   docker run --rm -p 8080:8080 --env-file backend/.env schulkasse-backend
#

# ----------------------------------------------------------------------
# 1) Build stage — runs Maven against the project sources.
# ----------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Resolve dependencies first so subsequent code-only changes hit the cache.
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -e -DskipTests dependency:go-offline

# Now compile + package.
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -e -DskipTests package && \
    cp target/kasse-backend.jar /workspace/app.jar

# ----------------------------------------------------------------------
# 2) Runtime stage — JRE only, smaller surface than the build image.
# ----------------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# curl is used by HEALTHCHECK below.
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/* && \
    groupadd --system --gid 1001 spring && \
    useradd  --system --gid 1001 --uid 1001 --create-home --home-dir /home/spring spring

COPY --from=build /workspace/app.jar /app/app.jar
USER spring

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=6 \
  CMD curl -s http://localhost:8080/api/categories -o /dev/null -w "%{http_code}" | grep -q "401"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
