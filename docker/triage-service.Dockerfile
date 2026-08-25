# Multi-stage build for triage-service
FROM maven:3.9.4-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN mvn -B -pl triage-service -am clean package

FROM eclipse-temurin:17-jdk
WORKDIR /app
EXPOSE 8095
COPY --from=build /workspace/triage-service/target/quarkus-app/lib/ /app/lib/
COPY --from=build /workspace/triage-service/target/quarkus-app/*.jar /app/
COPY --from=build /workspace/triage-service/target/quarkus-app/app/ /app/app/
COPY --from=build /workspace/triage-service/target/quarkus-app/quarkus/ /app/quarkus/
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
