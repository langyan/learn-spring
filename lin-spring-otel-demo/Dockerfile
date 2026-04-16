# 使用 maven 镜像进行多阶段构建
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# 运行镜像
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/target/lin-spring-otel-demo-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]