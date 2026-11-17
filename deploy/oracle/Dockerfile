# Build on the builder's architecture; the Java bytecode is architecture independent.
FROM --platform=$BUILDPLATFORM maven:3.9.16-eclipse-temurin-21@sha256:99e61abcff91a9b1333463bd8451fb18495d6eba9250ac66a338b518f8278320 AS build
WORKDIR /build
ENV MAVEN_OPTS="-Xmx512m"
COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-noble@sha256:0d57a8e90c2bb844c5a8e1b2b71e11eff06dad7edcfd803cd21ed4ae203b2515
RUN groupadd --system --gid 10001 shortener && useradd --system --uid 10001 --gid shortener --home /app shortener
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/target/high-performance-url-shortener-0.0.1-SNAPSHOT.jar app.jar
COPY --chmod=0555 deploy/oracle/container-health.sh /app/health.sh
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-Xms128m -Xmx384m -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=64m -XX:MaxDirectMemorySize=64m -Xss512k -XX:+UseG1GC -XX:ActiveProcessorCount=2 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 CMD ["/usr/bin/timeout", "3", "/bin/bash", "/app/health.sh"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
