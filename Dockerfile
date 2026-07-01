FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /workspace/target/high-performance-url-shortener-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8081
# JAVA_OPTS can be overridden per environment (e.g. Render free tier uses G1GC with lower heap)
# Default: ZGC at 75% RAM — suitable for 1GB+ instances
# Render free (512MB): set JAVA_OPTS=-XX:+UseG1GC -XX:MaxRAMPercentage=45 -XX:MaxMetaspaceSize=128m
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS:--XX:+UseZGC -XX:MaxRAMPercentage=75} -jar app.jar --server.port=${PORT:-8081}"]
