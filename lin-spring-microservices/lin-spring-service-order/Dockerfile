FROM eclipse-temurin:21-jre-alpine AS builder

WORKDIR /build

# Copy parent POM and Maven wrapper from root
COPY pom.xml .
COPY mvnw .
COPY mvnw.cmd .
COPY .mvn .mvn

# Copy Order Service module
COPY lin-spring-service-order/pom.xml ./lin-spring-service-order/
COPY lin-spring-service-order/src ./lin-spring-service-order/src

# Build the application
RUN chmod +x mvnw && ./mvnw clean package -DskipTests -pl lin-spring-service-order -am

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

# Install curl for health checks
RUN apk add --no-cache curl

WORKDIR /app

# Copy jar from builder
COPY --from=builder /build/lin-spring-service-order/target/*.jar app.jar

# Expose port
EXPOSE 8081

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
