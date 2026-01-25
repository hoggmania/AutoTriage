# Multi-stage build for suppression-service-mock
FROM maven:3.9.4-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN mvn -pl suppression-service-mock -am clean package -DskipTests

FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY --from=build /workspace/suppression-service-mock/target/*.jar /app/app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
