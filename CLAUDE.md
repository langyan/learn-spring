# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a comprehensive Spring Boot learning repository containing multiple independent modules that demonstrate various Spring framework features and integrations. Each module is a standalone Spring Boot application focused on a specific Spring concept or integration.

## Repository Structure

The repository is organized as multiple independent Maven modules, each demonstrating a different Spring concept:

- **lin-spring-validation** - Bean validation with Hibernate Validator
- **lin-spring-greetings-api-web** - Traditional web MVC application
- **lin-spring-greetings-api-reactive** - Reactive web application with WebFlux
- **lin-spring-transactional** - Transaction management with JPA
- **lin-spring-resilience4j-bulkhead** - Resilience patterns with Resilience4j
- **lin-spring-bean-lifecycle** - Spring bean lifecycle management
- **lin-spring-jpa** - JPA and Hibernate integration
- **lin-spring-security-dao-auth** - Security with JWT and database authentication
- **lin-spring-redis** - Redis caching and pub/sub
- **lin-spring-mongodb** - MongoDB integration
- **lin-spring-otp** - OTP authentication system
- **lin-spring-keycloak** - Keycloak integration for authentication
- **lin-spring-websocket** - WebSocket communication
- **lin-spring-file-upload** - File upload handling
- **lin-spring-jackson-deserialization** - Jackson polymorphic deserialization
- **lin-spring-micrometer** - Application metrics with Micrometer
- **lin-spring-loki** - Logging with Loki
- **lin-spring-k8s** - Kubernetes deployment examples
- **lin-spring-docker-images** - Docker containerization

## Common Development Tasks

### Building and Running Applications

Each module is a standalone Spring Boot application. To build and run any module:

```bash
# Navigate to the module directory
cd lin-spring-{module-name}

# Build the application
./mvnw clean compile

# Run the application
./mvnw spring-boot:run

# Run tests
./mvnw test
```

### Key Dependencies and Versions

- **Spring Boot**: Multiple versions (3.4.4, 3.5.0, 3.5.3) across modules
- **Java**: Java 17 or 21 depending on module
- **Lombok**: Used extensively for reducing boilerplate code
- **DevTools**: Included for development hot-reload
- **Testing**: Spring Boot Test with JUnit 5

### Architecture Patterns

Each module follows consistent Spring Boot patterns:

1. **Application Class**: `@SpringBootApplication` annotated main class
2. **Controllers**: REST controllers with `@RestController` and `@RequestMapping`
3. **Services**: Business logic in `@Service` annotated classes
4. **Repositories**: Data access with Spring Data JPA repositories
5. **DTOs**: Request/response objects with validation annotations
6. **Configuration**: Configuration classes with `@Configuration`
7. **Exception Handling**: Global exception handlers with `@ControllerAdvice`

### Code Style and Conventions

- **Package Structure**: `com.lin.spring.{module}.{layer}` (controller, service, repository, etc.)
- **Constructor Injection**: Prefer `@Autowired` on constructor or `@RequiredArgsConstructor`
- **Validation**: Use `@Valid` on controller method parameters
- **Logging**: SLF4J with `LoggerFactory.getLogger()`
- **Error Handling**: Consistent ResponseEntity patterns in controllers

### Testing

Each module includes test files in `src/test/java/`:

- Unit tests for services and controllers
- Integration tests with `@SpringBootTest`
- Test configurations in `src/test/resources/`
- HTTP test files (`api.http`) for manual API testing

### Database Integration

Modules with database integration typically use:
- **H2 Database**: In-memory database for testing
- **Spring Data JPA**: Repository interfaces
- **Entity Classes**: JPA entities with `@Entity` annotation
- **Transaction Management**: `@Transactional` on service methods

### Security

Security modules demonstrate:
- **JWT Authentication**: Custom JWT filters and services
- **Database Authentication**: Custom UserDetailsService implementations
- **Security Configuration**: `@EnableWebSecurity` configurations
- **Role-based Access**: `@PreAuthorize` annotations

### API Documentation

Many modules include HTTP test files (`api.http`) that can be used with HTTP client tools to test the APIs manually.

### Module-Specific Notes

- **Reactive Modules**: Use WebFlux and reactive streams
- **Resilience Modules**: Include Resilience4j with AOP configuration
- **WebSocket Modules**: Include STOMP configuration and message handling
- **File Upload**: Demonstrate chunked upload and file handling
- **Metrics**: Include Micrometer configuration for Prometheus

## Development Workflow

1. Work within individual module directories
2. Use `./mvnw spring-boot:run` for local development
3. Leverage Spring Boot DevTools for automatic restarts
4. Test APIs using the provided `api.http` files
5. Check module-specific README files for additional instructions

## Important Notes

- Each module is independent and can be developed/tested separately
- No parent POM - each module has its own complete Maven configuration
- Some modules use different Spring Boot versions and Java versions
- Database configurations are typically for H2 in-memory database
- Security modules may require external services (Keycloak, Redis, etc.)