# ----- STAGE 1: Build -----
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -pl gateway -am -DskipTests

# ----- STAGE 2: Run -----
FROM eclipse-temurin:21-jdk AS run
WORKDIR /app
COPY --from=build app/tracker/target/*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
