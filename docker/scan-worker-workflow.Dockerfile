# Multi-stage build for scan-worker-workflow
FROM maven:3.9.4-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN mvn -pl scan-worker-workflow -am clean package -DskipTests

FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY --from=build /workspace/scan-worker-workflow/target/quarkus-app/lib/ /app/lib/
COPY --from=build /workspace/scan-worker-workflow/target/quarkus-app/*.jar /app/
COPY --from=build /workspace/scan-worker-workflow/target/quarkus-app/app/ /app/app/
COPY --from=build /workspace/scan-worker-workflow/target/quarkus-app/quarkus/ /app/quarkus/
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
