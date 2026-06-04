FROM eclipse-temurin:21-jre

WORKDIR /app
COPY target/high-performance-url-shortener-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
