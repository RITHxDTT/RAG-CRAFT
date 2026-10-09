# Build context is the services/ folder: docker build -f conversation-service/Dockerfile .
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY identity-service/pom.xml identity-service/pom.xml
COPY catalog-service/pom.xml catalog-service/pom.xml
COPY chatbot-service/pom.xml chatbot-service/pom.xml
COPY knowledge-service/pom.xml knowledge-service/pom.xml
COPY conversation-service/pom.xml conversation-service/pom.xml
COPY channel-service/pom.xml channel-service/pom.xml
COPY analytics-service/pom.xml analytics-service/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY common/src common/src
COPY conversation-service/src conversation-service/src
RUN mvn -q -pl conversation-service -am -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/conversation-service/target/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
