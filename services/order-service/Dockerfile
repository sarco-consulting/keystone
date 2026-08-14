# Packages an already-built jar rather than building inside the image: CI
# runs `./gradlew build` once (compiling all four modules, running the full
# test pyramid) and this just packages that output — no point recompiling a
# multi-module Gradle project a second time inside Docker.
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY build/libs/order-service-*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
