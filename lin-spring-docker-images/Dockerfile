# Stage 1: Build
# --------------
FROM eclipse-temurin:22-jdk AS builder
COPY . /app
WORKDIR /app
RUN ./mvnw package -DskipTests

# Stage 2: Runtime
# ----------------
FROM eclipse-temurin:22-jre AS runtime
WORKDIR /app
ARG JAR_FILE=/app/target/*.jar
COPY --from=builder ${JAR_FILE} app.jar
RUN java -Djarmode=layertools -jar app.jar extract
COPY --from=builder /app/dependencies/ ./dependencies/
COPY --from=builder /app/spring-boot-loader/ ./spring-boot-loader/
COPY --from=builder /app/snapshot-dependencies/ ./snapshot-dependencies/
COPY --from=builder /app/application/ ./application/
ENTRYPOINT ["java", "org.springframework.boot.loader.JarLauncher"]