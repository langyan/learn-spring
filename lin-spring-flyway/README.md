# Spring Boot Flyway Demo

This module demonstrates database migration using Flyway with Spring Boot.

## Overview

Flyway is an open-source database migration tool that favors simplicity and convention over configuration. It helps you manage and version your database changes.

## Features

- **Database Migration**: Automatic schema versioning and migration with Flyway
- **H2 In-Memory Database**: Fast, lightweight database for development
- **RESTful API**: Complete CRUD operations for user management
- **Validation**: Bean validation with Jakarta Validation
- **H2 Console**: Web-based database console for inspection

## Technology Stack

- **Spring Boot**: 3.4.5
- **Java**: 21
- **Flyway**: flyway-core (Spring Boot 3.x compatible)
- **H2 Database**: In-memory database
- **Spring Data JPA**: For data access
- **Lombok**: For reducing boilerplate code
- **JUnit 5**: For testing

## Project Structure

```
lin-spring-flyway/
├── src/
│   ├── main/
│   │   ├── java/com/lin/spring/flyway/
│   │   │   ├── LinSpringFlywayApplication.java    # Main application class
│   │   │   ├── controller/
│   │   │   │   └── UserController.java            # REST controller
│   │   │   ├── service/
│   │   │   │   └── UserService.java               # Business logic
│   │   │   ├── repository/
│   │   │   │   └── UserRepository.java            # Data access layer
│   │   │   └── model/
│   │   │       └── User.java                      # Entity class
│   │   └── resources/
│   │       ├── application.properties             # Application configuration
│   │       └── db/migration/                      # Flyway migration scripts
│   │           ├── V1__Create_users_table.sql
│   │           ├── V2__Insert_sample_data.sql
│   │           └── V3__Add_last_login_column.sql
│   └── test/
│       └── java/com/lin/spring/flyway/
│           └── controller/
│               └── UserControllerTest.java        # Integration tests
└── api.http                                       # API test file
```

## Flyway Migration Scripts

Migration scripts are located in `src/main/resources/db/migration/` and follow the naming convention:

```
V{version}__{description}.sql
```

### Available Migrations

1. **V1__Create_users_table.sql**: Creates the users table with indexes
2. **V2__Insert_sample_data.sql**: Inserts sample user data
3. **V3__Add_last_login_column.sql**: Adds last_login column to users table

## Running the Application

### Using Maven

```bash
cd lin-spring-flyway
./mvnw spring-boot:run
```

### Using IDE

Run the `LinSpringFlywayApplication` class directly.

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/users` | Get all users |
| GET | `/api/users/{id}` | Get user by ID |
| GET | `/api/users/username/{username}` | Get user by username |
| POST | `/api/users` | Create new user |
| PUT | `/api/users/{id}` | Update user |
| DELETE | `/api/users/{id}` | Delete user |
| GET | `/api/users/stats` | Get user statistics |

## H2 Console

Access the H2 console at: `http://localhost:8080/h2-console`

**Connection Details:**
- JDBC URL: `jdbc:h2:mem:flywaydb`
- Username: `sa`
- Password: (leave empty)

## Flyway Commands

### Check Migration Status

```bash
./mvnw flyway:info
```

### Migrate Database

```bash
./mvnw flyway:migrate
```

### Validate Migrations

```bash
./mvnw flyway:validate
```

### Clean Database

```bash
./mvnw flyway:clean
```

## Configuration

Key Flyway configurations in `application.properties`:

```properties
# Enable Flyway
spring.flyway.enabled=true

# Baseline on migrate (for existing databases)
spring.flyway.baseline-on-migrate=true

# Migration scripts location
spring.flyway.locations=classpath:db/migration

# Validate migrations before running
spring.flyway.validate-on-migrate=true
```

## Testing

Run tests with:

```bash
./mvnw test
```

## Sample Request/Response

### Create User

**Request:**
```http
POST /api/users
Content-Type: application/json

{
  "username": "john_doe",
  "email": "john@example.com",
  "password": "password123",
  "phone": "+1234567890",
  "address": "123 Main St",
  "enabled": true
}
```

**Response:**
```json
{
  "success": true,
  "message": "User created successfully",
  "data": {
    "id": 1,
    "username": "john_doe",
    "email": "john@example.com",
    "phone": "+1234567890",
    "address": "123 Main St",
    "enabled": true,
    "createdAt": "2024-01-01T10:00:00",
    "updatedAt": "2024-01-01T10:00:00"
  }
}
```

## Key Concepts

### Flyway Versioning

Flyway uses a versioning scheme for migrations:

- **Versioned migrations**: V1, V2, V3, etc. - applied in order
- **Repeatable migrations**: R__ - applied each time if checksum changes
- **Undo migrations**: U__ - for undoing changes (requires Flyway Teams)

### Migration States

1. **Pending**: Migration not yet applied
2. **Success**: Migration applied successfully
3. **Failed**: Migration failed (requires manual intervention)
4. **Out of order**: Migration applied out of sequence

### Best Practices

1. **One change per migration**: Keep migrations focused
2. **Never modify existing migrations**: Create new ones instead
3. **Use transactions**: Wrap migrations in transactions when possible
4. **Test migrations**: Always test migrations before production
5. **Version control**: Keep migration scripts in version control
6. **Backward compatible**: Design migrations to be backward compatible

## Further Reading

- [Flyway Documentation](https://flywaydb.org/documentation/)
- [Spring Boot Flyway Starter](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.data-initialization.migration-tool.flyway)
- [Database Migration Best Practices](https://flywaydb.org/documentation/bestpractices)
