FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml ./
COPY gateway ./gateway
COPY node ./node
COPY tracker ./tracker

# Build node module
RUN mvn clean package -pl gateway -am -DskipTests

FROM eclipse-temurin:21-jdk AS run
WORKDIR /app
COPY --from=build /app/node/target/node-1.0-SNAPSHOT.jar ./node.jar

EXPOSE 2206-2210/udp
EXPOSE 8082-8086
ENTRYPOINT ["java","-jar","node.jar"]
