# Build context is the repository root:  docker build -f config-server/Dockerfile .
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY config-server/pom.xml config-server/
RUN mvn -B -q -f config-server/pom.xml dependency:go-offline
COPY config-server/src config-server/src
RUN mvn -B -q -f config-server/pom.xml package -DskipTests

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/config-server/target/*.jar app.jar
USER app
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "app.jar"]
