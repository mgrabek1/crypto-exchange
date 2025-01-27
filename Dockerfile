FROM openjdk:17-jdk-slim

WORKDIR /app

COPY target/crypto-exchange-1.0-SNAPSHOT.jar crypto-exchange.jar

ENV SPRING_PROFILES_ACTIVE=docker

ENTRYPOINT ["java", "-jar", "crypto-exchange.jar"]
