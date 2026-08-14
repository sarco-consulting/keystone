# See order-service/Dockerfile for why this packages a pre-built jar.
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY build/libs/payment-service-*.jar app.jar

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
