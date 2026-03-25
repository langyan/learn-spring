FROM eclipse-temurin:21-jre-alpine AS builder

WORKDIR /build

# Copy parent POM and Maven wrapper from root
COPY pom.xml .
COPY mvnw .
COPY mvnw.cmd .
COPY .mvn .mvn

# Copy Eureka Server module
COPY lin-spring-cloud-eureka/pom.xml ./lin-spring-cloud-eureka/
COPY lin-spring-cloud-eureka/src ./lin-spring-cloud-eureka/src

# Build the application
RUN chmod +x mvnw && ./mvnw clean package -DskipTests -pl lin-spring-cloud-eureka -am

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

# Install curl for health checks
RUN apk add --no-cache curl

WORKDIR /app

# Copy jar from builder
COPY --from=builder /build/lin-spring-cloud-eureka/target/*.jar app.jar

# Expose port
EXPOSE 8761

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
