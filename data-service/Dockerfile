FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml .
COPY data-service/pom.xml data-service/pom.xml
RUN mvn -q -pl data-service -am dependency:go-offline

COPY data-service data-service
RUN mvn -q -pl data-service -am -DskipTests package

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /workspace/data-service/target/data-service-*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
