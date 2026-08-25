# Multi-stage build for scan-worker-opengrep
FROM maven:3.9.4-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN mvn -B -pl scan-worker-opengrep -am clean package

FROM eclipse-temurin:17-jdk
RUN apt-get update \
    && apt-get install -y --no-install-recommends bubblewrap ca-certificates \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/scan-worker-opengrep/target/quarkus-app/lib/ /app/lib/
COPY --from=build /workspace/scan-worker-opengrep/target/quarkus-app/*.jar /app/
COPY --from=build /workspace/scan-worker-opengrep/target/quarkus-app/app/ /app/app/
COPY --from=build /workspace/scan-worker-opengrep/target/quarkus-app/quarkus/ /app/quarkus/
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
