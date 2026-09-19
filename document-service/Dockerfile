# Build context is the repository root:  docker build -f document-service/Dockerfile .
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY common-lib common-lib
COPY document-service/pom.xml document-service/
RUN mvn -B -q -f common-lib/pom.xml install -DskipTests
RUN mvn -B -q -f document-service/pom.xml dependency:go-offline
COPY document-service/src document-service/src
RUN mvn -B -q -f document-service/pom.xml package -DskipTests

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/document-service/target/*.jar app.jar
USER app
EXPOSE 8089
ENTRYPOINT ["java", "-jar", "app.jar"]
