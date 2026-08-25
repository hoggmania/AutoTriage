# Multi-stage build for scan-worker-light
FROM maven:3.9.4-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN mvn -B -pl scan-worker-light -am clean package

FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY --from=build /workspace/scan-worker-light/target/quarkus-app/lib/ /app/lib/
COPY --from=build /workspace/scan-worker-light/target/quarkus-app/*.jar /app/
COPY --from=build /workspace/scan-worker-light/target/quarkus-app/app/ /app/app/
COPY --from=build /workspace/scan-worker-light/target/quarkus-app/quarkus/ /app/quarkus/
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
