# Multi-stage build for triage-service
FROM maven:3.9.4-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN mvn -pl triage-service -am clean package -DskipTests

FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY --from=build /workspace/triage-service/target/*.jar /app/app.jar
EXPOSE 8095
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
