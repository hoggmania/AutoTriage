# Multi-stage build for scan-worker-filter
FROM maven:3.9.4-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN mvn -pl scan-worker-filter -am clean package -DskipTests

FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY --from=build /workspace/scan-worker-filter/target/quarkus-app/lib/ /app/lib/
COPY --from=build /workspace/scan-worker-filter/target/quarkus-app/*.jar /app/
COPY --from=build /workspace/scan-worker-filter/target/quarkus-app/app/ /app/app/
COPY --from=build /workspace/scan-worker-filter/target/quarkus-app/quarkus/ /app/quarkus/
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
