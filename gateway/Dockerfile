# ----- STAGE 1: Build -----
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
COPY gateway ./gateway
COPY node ./node
COPY tracker ./tracker
RUN mvn clean package -pl gateway -am -DskipTests

# ----- STAGE 2: Run -----
FROM eclipse-temurin:21-jdk AS run
WORKDIR /app
COPY --from=build /app/gateway/target/gateway-1.0-SNAPSHOT.jar ./gateway.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","gateway.jar"]
