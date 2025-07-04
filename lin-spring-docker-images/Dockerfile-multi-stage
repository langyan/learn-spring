FROM eclipse-temurin:22-jdk AS builder
COPY . /app
WORKDIR /app
RUN ./mvnw package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:22-jre
COPY --from=builder /app/target/*.jar /app.jar
CMD ["java", "-jar", "/app.jar"]