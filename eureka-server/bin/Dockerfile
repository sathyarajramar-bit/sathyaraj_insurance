# Build context is the repository root:  docker build -f eureka-server/Dockerfile .
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY eureka-server/pom.xml eureka-server/
RUN mvn -B -q -f eureka-server/pom.xml dependency:go-offline
COPY eureka-server/src eureka-server/src
RUN mvn -B -q -f eureka-server/pom.xml package -DskipTests

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/eureka-server/target/*.jar app.jar
USER app
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]
