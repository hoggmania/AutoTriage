# Multi-stage build for suppression-service-mock
FROM maven:3.9.4-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN mvn -B -pl suppression-service-mock -am clean package

FROM eclipse-temurin:17-jdk
WORKDIR /app
EXPOSE 8090
COPY --from=build /workspace/suppression-service-mock/target/quarkus-app/lib/ /app/lib/
COPY --from=build /workspace/suppression-service-mock/target/quarkus-app/*.jar /app/
COPY --from=build /workspace/suppression-service-mock/target/quarkus-app/app/ /app/app/
COPY --from=build /workspace/suppression-service-mock/target/quarkus-app/quarkus/ /app/quarkus/
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
