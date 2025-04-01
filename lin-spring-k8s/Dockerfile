# Use a lightweight Java image
FROM openjdk:17-jdk-alpine

# Set the JAR file path
ARG JAR_FILE=target/*.jar

# Copy the JAR file into the container
COPY ${JAR_FILE} app.jar

# Run the application
ENTRYPOINT ["java", "-jar", "/app.jar"]

