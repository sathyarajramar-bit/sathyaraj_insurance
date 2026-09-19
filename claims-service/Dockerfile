# Build context is the repository root:  docker build -f claims-service/Dockerfile .
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY common-lib common-lib
COPY claims-service/pom.xml claims-service/
RUN mvn -B -q -f common-lib/pom.xml install -DskipTests
RUN mvn -B -q -f claims-service/pom.xml dependency:go-offline
COPY claims-service/src claims-service/src
RUN mvn -B -q -f claims-service/pom.xml package -DskipTests

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/claims-service/target/*.jar app.jar
USER app
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
