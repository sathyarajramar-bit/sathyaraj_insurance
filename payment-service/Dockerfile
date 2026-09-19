# Build context is the repository root:  docker build -f payment-service/Dockerfile .
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY common-lib common-lib
COPY payment-service/pom.xml payment-service/
RUN mvn -B -q -f common-lib/pom.xml install -DskipTests
RUN mvn -B -q -f payment-service/pom.xml dependency:go-offline
COPY payment-service/src payment-service/src
RUN mvn -B -q -f payment-service/pom.xml package -DskipTests

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/payment-service/target/*.jar app.jar
USER app
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "app.jar"]
