FROM eclipse-temurin:21-jre-alpine AS builder

WORKDIR /build

# Copy parent POM and Maven wrapper from root
COPY pom.xml .
COPY mvnw .
COPY mvnw.cmd .
COPY .mvn .mvn

# Copy Shipping Service module
COPY lin-spring-service-shipping/pom.xml ./lin-spring-service-shipping/
COPY lin-spring-service-shipping/src ./lin-spring-service-shipping/src

# Build the application
RUN chmod +x mvnw && ./mvnw clean package -DskipTests -pl lin-spring-service-shipping -am

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

# Install curl for health checks
RUN apk add --no-cache curl

WORKDIR /app

# Copy jar from builder
COPY --from=builder /build/lin-spring-service-shipping/target/*.jar app.jar

# Expose port
EXPOSE 8084

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
