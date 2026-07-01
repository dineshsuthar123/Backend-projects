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
# Use the PORT environment variable provided by Render; fall back to 8081 locally
ENTRYPOINT ["sh", "-c", "exec java -XX:+UseZGC -XX:MaxRAMPercentage=75 -jar app.jar --server.port=${PORT:-8081}"]
