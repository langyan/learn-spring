FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Install ca-certificates and update them
RUN apk add --no-cache ca-certificates wget && \
    update-ca-certificates

# Copy parent POM and Maven wrapper from root
COPY pom.xml .
COPY mvnw .
COPY mvnw.cmd .
COPY .mvn .mvn

# Copy all module POMs (required for parent POM validation)
COPY lin-spring-cloud-eureka/pom.xml ./lin-spring-cloud-eureka/
COPY lin-spring-cloud-config/pom.xml ./lin-spring-cloud-config/
COPY lin-spring-cloud-gateway/pom.xml ./lin-spring-cloud-gateway/
COPY lin-spring-service-user/pom.xml ./lin-spring-service-user/
COPY lin-spring-service-order/pom.xml ./lin-spring-service-order/
COPY lin-spring-service-payment/pom.xml ./lin-spring-service-payment/
COPY lin-spring-service-inventory/pom.xml ./lin-spring-service-inventory/
COPY lin-spring-service-shipping/pom.xml ./lin-spring-service-shipping/

# Copy User Service source
COPY lin-spring-service-user/src ./lin-spring-service-user/src

# Build the application
RUN chmod +x mvnw && ./mvnw clean package -DskipTests -pl lin-spring-service-user -am

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

# Install curl for health checks and ca-certificates
RUN apk add --no-cache curl ca-certificates && \
    update-ca-certificates

WORKDIR /app

# Copy jar from builder
COPY --from=builder /build/lin-spring-service-user/target/*.jar app.jar

# Expose port
EXPOSE 8085

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
