# Multi-stage build for scan-worker-light
FROM maven:3.9.4-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN mvn -pl scan-worker-light -am clean package -DskipTests

FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY --from=build /workspace/scan-worker-light/target/*.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
