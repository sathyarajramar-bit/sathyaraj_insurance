# Build context is the repository root:  docker build -f notification-service/Dockerfile .
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY common-lib common-lib
COPY notification-service/pom.xml notification-service/
RUN mvn -B -q -f common-lib/pom.xml install -DskipTests
RUN mvn -B -q -f notification-service/pom.xml dependency:go-offline
COPY notification-service/src notification-service/src
RUN mvn -B -q -f notification-service/pom.xml package -DskipTests

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/notification-service/target/*.jar app.jar
USER app
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "app.jar"]
