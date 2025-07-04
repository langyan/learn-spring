

# Non-Optimized Dockerfile
```
FROM eclipse-temurin:22-jdk  
COPY . /app  
WORKDIR /app  
RUN ./mvnw package  
CMD ["java", "-jar", "target/app.jar"]  # Image size: ~890MB
```

## 打包 mvn
```
E:\source_files\learn-spring\lin-spring-docker-images> mvn clean package -DskipTests
```

## 镜像 docker build -t lin-spring-docker-images:non-optimized .
```
E:\source_files\learn-spring\lin-spring-docker-images> docker build -t lin-spring-docker-images:non-optimized .
```
## 查看镜像 docker images
``` 
PS E:\source_files\learn-spring\lin-spring-docker-images> docker images lin-spring-docker-images:non-optimized    
REPOSITORY                 TAG             IMAGE ID       CREATED              SIZE
lin-spring-docker-images   non-optimized   dd234f57949e   About a minute ago   669MB

```

# Optimized with Multi-Stage
```
# Stage 1: Build
FROM eclipse-temurin:22-jdk AS builder
COPY . /app
WORKDIR /app
RUN ./mvnw package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:22-jre
COPY --from=builder /app/target/*.jar /app.jar
CMD ["java", "-jar", "/app.jar"]
```

## 镜像 docker build -t lin-spring-docker-images:multi-stage .
````
PS E:\source_files\learn-spring\lin-spring-docker-images> docker build -t lin-spring-docker-images:multi-stage .  
[+] Building 77.0s (9/11)   
````
## 查看镜像 docker images
````
PS E:\source_files\learn-spring\lin-spring-docker-images> docker images lin-spring-docker-images                
REPOSITORY                 TAG             IMAGE ID       CREATED          SIZE
lin-spring-docker-images   multi-stage     0e5b70730858   59 seconds ago   340MB
lin-spring-docker-images   non-optimized   dd234f57949e   15 minutes ago   669MB

````

# Spring Boot Layer Tools: Split Dependencies

```
PS E:\source_files\learn-spring\lin-spring-docker-images\target> java -Djarmode=layertools -jar  lin-spring-docker-0.0.1-SNAPSHOT.jar  extract
Warning: This command is deprecated. Use '-Djarmode=tools extract --layers --launcher' instead.

PS E:\source_files\learn-spring\lin-spring-docker-images\target> cd ..
PS E:\source_files\learn-spring\lin-spring-docker-images> docker build -t lin-spring-docker-images:layer .   
```
