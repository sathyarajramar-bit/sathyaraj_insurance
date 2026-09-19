# Build context is the repository root:  docker build -f policy-service/Dockerfile .
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY common-lib common-lib
COPY policy-service/pom.xml policy-service/
RUN mvn -B -q -f common-lib/pom.xml install -DskipTests
RUN mvn -B -q -f policy-service/pom.xml dependency:go-offline
COPY policy-service/src policy-service/src
RUN mvn -B -q -f policy-service/pom.xml package -DskipTests

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/policy-service/target/*.jar app.jar
USER app
EXPOSE 8087
ENTRYPOINT ["java", "-jar", "app.jar"]
