# Spring Boot + Liquibase Demo

This module demonstrates database migration using **Liquibase** with Spring Boot.

## Overview

Liquibase is an open-source database-independent library for tracking, managing and applying database schema changes. It works with various database types and supports multiple changelog formats (XML, YAML, JSON, SQL).

## Features Demonstrated

1. **Liquibase Integration**: Automatic database migration on application startup
2. **Multiple Changelog Formats**: YAML format for better readability
3. **ChangeSets**: Version-controlled database schema changes
4. **Sample Data Loading**: Initial data insertion via Liquibase
5. **Schema Evolution**: Adding new columns and indexes

## Technologies

- **Spring Boot 3.4.5**
- **Liquibase 4.29.2**
- **Spring Data JPA**
- **H2 In-Memory Database**
- **Lombok**
- **JUnit 5** for testing

## Project Structure

```
lin-spring-liquibase/
├── src/main/java/com/lin/spring/liquibase/
│   ├── controller/          # REST Controllers
│   ├── service/             # Business Logic
│   ├── repository/          # Data Access Layer
│   ├── model/               # JPA Entities
│   ├── dto/                 # Request/Response Objects
│   └── exception/           # Exception Handling
├── src/main/resources/
│   ├── db/changelog/        # Liquibase Changelogs
│   │   ├── db.changelog-master.yaml
│   │   └── changeset/       # Individual ChangeSets
│   └── application.properties
└── src/test/                # Integration Tests
```

## Liquibase Changelog Structure

```yaml
# db.changelog-master.yaml - Main entry point
databaseChangeLog:
  - include:
      file: db/changelog/changeset/create-users-table.yaml
  - include:
      file: db/changelog/changeset/insert-sample-data.yaml
  - include:
      file: db/changelog/changeset/add-status-column.yaml
```

### ChangeSet Examples

1. **Create Table**: `create-users-table.yaml`
2. **Insert Data**: `insert-sample-data.yaml`
3. **Add Columns**: `add-status-column.yaml`

## Getting Started

### Build and Run

```bash
# Navigate to the module directory
cd lin-spring-liquibase

# Build the project
./mvnw clean compile

# Run the application
./mvnw spring-boot:run

# Run tests
./mvnw test
```

### Access the Application

- **Application URL**: http://localhost:8080
- **H2 Console**: http://localhost:8080/h2-console
  - JDBC URL: `jdbc:h2:mem:testdb`
  - Username: `sa`
  - Password: (leave empty)

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/users` | Get all users |
| GET | `/api/users/active` | Get active users only |
| GET | `/api/users/{id}` | Get user by ID |
| GET | `/api/users/username/{username}` | Get user by username |
| POST | `/api/users` | Create new user |
| PUT | `/api/users/{id}` | Update user |
| DELETE | `/api/users/{id}` | Delete user |
| PATCH | `/api/users/{id}/disable` | Disable user |
| PATCH | `/api/users/{id}/enable` | Enable user |
| POST | `/api/users/{id}/login` | Record login timestamp |

## Liquibase Commands

### Using Maven Plugin

```bash
# Update database to current version
./mvnw liquibase:update

# Check database status
./mvnw liquibase:status

# Rollback last changeset
./mvnw liquibase:rollback

# Generate SQL for pending changes
./mvnw liquibase:updateSQL

# List all changesets
./mvnw liquibase:listLocks
```

### Liquibase Tables

After running the application, Liquibase creates two tracking tables:

1. **DATABASECHANGELOG**: Records all executed changesets
2. **DATABASECHANGELOGLOCK**: Prevents concurrent migrations

Query in H2 Console:
```sql
SELECT * FROM DATABASECHANGELOG ORDER BY DATEEXECUTED;
```

## Changelog Formats

Liquibase supports multiple formats:

### YAML (Used in this demo)
```yaml
databaseChangeLog:
  - changeSet:
      id: create-users-table
      author: lin
      changes:
        - createTable:
            tableName: users
            columns:
              - column:
                  name: id
                  type: BIGINT
```

### SQL
```sql
-- liquibase formatted sql
-- changeset lin:create-users-table
CREATE TABLE users (
    id BIGINT PRIMARY KEY,
    username VARCHAR(50)
);
```

### XML
```xml
<changeSet id="create-users-table" author="lin">
    <createTable tableName="users">
        <column name="id" type="BIGINT">
            <constraints primaryKey="true"/>
        </column>
    </createTable>
</changeSet>
```

## Best Practices

1. **One Change Per ChangeSet**: Each changeset should make one atomic change
2. **Id + Author**: Unique combination identifies each changeset
3. **Rollback Support**: Include rollback statements for production
4. **Context-based Changes**: Use contexts for dev/prod differences
5. **Immutable Changesets**: Never modify existing changesets

## Configuration

Key properties in `application.properties`:

```properties
# Enable Liquibase
spring.liquibase.enabled=true

# Changelog location
spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml

# Context (development, production, etc.)
spring.liquibase.contexts=development
```

## Running Tests

```bash
# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=UserControllerTest
```

## License

This is a learning demo project.
