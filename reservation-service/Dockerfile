# Stage 1: Build using official Maven image (has 'mvn' pre-installed)
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy entire project structure
COPY pom.xml .
COPY commons commons
COPY inventory-service inventory-service
COPY order-service order-service
COPY reservation-service reservation-service

# Build the application
RUN mvn clean package -DskipTests -pl reservation-service -am

# Stage 2: Run the application
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the built jar from the build stage
COPY --from=build /app/reservation-service/target/*.jar app.jar

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
