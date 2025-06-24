# Contributing to Spring Boot Tracer Library

Thank you for your interest in contributing to the Spring Boot Tracer Library! This document provides guidelines and information for contributors.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [How to Contribute](#how-to-contribute)
- [Pull Request Process](#pull-request-process)
- [Coding Standards](#coding-standards)
- [Testing Guidelines](#testing-guidelines)
- [Documentation](#documentation)
- [Release Process](#release-process)

## Code of Conduct

This project adheres to a code of conduct that we expect all contributors to follow. Please be respectful, inclusive, and constructive in all interactions.

## Getting Started

### Prerequisites

- **Java 17+** (OpenJDK recommended)
- **Gradle 8.0+**
- **Docker** (for database testing)
- **Git**

### Supported Databases for Testing

- PostgreSQL 13+
- MySQL 8.0+
- MariaDB 10.6+
- MongoDB 5.0+

## Development Setup

### 1. Fork and Clone

```bash
# Fork the repository on GitHub
git clone https://github.com/your-username/tracer.git
cd tracer
```

### 2. Build the Project

```bash
# Build and run tests
./gradlew build

# Run tests with specific database
./gradlew test -Dspring.profiles.active=postgresql
```

### 3. Database Setup for Testing

**Using Docker Compose:**

```yaml
# docker-compose.yml for development
version: '3.8'
services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: tracing_test
      POSTGRES_USER: tracer
      POSTGRES_PASSWORD: password
    ports:
      - "5432:5432"
      
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_DATABASE: tracing_test
      MYSQL_USER: tracer
      MYSQL_PASSWORD: password
      MYSQL_ROOT_PASSWORD: rootpassword
    ports:
      - "3306:3306"
      
  mongodb:
    image: mongo:5.0
    environment:
      MONGO_INITDB_DATABASE: tracing_test
    ports:
      - "27017:27017"
```

```bash
docker-compose up -d
```

### 4. IDE Setup

**IntelliJ IDEA:**
1. Import as Gradle project
2. Enable annotation processing
3. Install CheckStyle plugin
4. Import code style from `docs/intellij-codestyle.xml`

**Eclipse:**
1. Import as existing Gradle project
2. Install Gradle BuildShip plugin
3. Configure Java 17 compliance

## How to Contribute

### Types of Contributions

1. **Bug Reports** - Help us identify and fix issues
2. **Feature Requests** - Suggest new capabilities
3. **Code Contributions** - Implement features or fixes
4. **Documentation** - Improve guides and examples
5. **Testing** - Add test coverage or improve test quality

### Reporting Bugs

Before creating a bug report:
1. **Search existing issues** to avoid duplicates
2. **Test with the latest version** to ensure the bug still exists
3. **Provide minimal reproduction case**

**Bug Report Template:**
```markdown
## Bug Description
Brief description of the issue

## Environment
- Tracer Version: 
- Spring Boot Version:
- Database: (PostgreSQL/MySQL/MariaDB/MongoDB)
- Java Version:
- OS:

## Steps to Reproduce
1. Step one
2. Step two
3. Step three

## Expected Behavior
What you expected to happen

## Actual Behavior
What actually happened

## Minimal Reproduction
Link to repository or code snippet that demonstrates the issue

## Additional Context
Any other relevant information
```

### Suggesting Features

Feature requests should include:
1. **Use case description** - Why is this needed?
2. **Proposed solution** - How should it work?
3. **Alternatives considered** - What other approaches were considered?
4. **Backward compatibility** - How does this affect existing users?

## Pull Request Process

### 1. Before Starting

- **Create an issue** to discuss significant changes
- **Check existing PRs** to avoid duplicate work
- **Review the roadmap** to ensure alignment with project goals

### 2. Development Workflow

```bash
# Create feature branch
git checkout -b feature/your-feature-name

# Make changes and commit
git add .
git commit -m "feat: add new feature description"

# Keep your branch updated
git fetch upstream
git rebase upstream/main

# Push to your fork
git push origin feature/your-feature-name
```

### 3. Commit Message Convention

We follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

**Types:**
- `feat:` - New feature
- `fix:` - Bug fix
- `docs:` - Documentation changes
- `style:` - Code style changes (formatting, etc.)
- `refactor:` - Code refactoring
- `test:` - Adding or updating tests
- `chore:` - Maintenance tasks

**Examples:**
```
feat(mongodb): add aggregation pipeline support for date grouping

fix(jdbc): resolve deprecated JdbcTemplate method warnings

docs: update configuration examples for MongoDB

test(postgres): add integration tests for partitioning
```

### 4. Pull Request Template

```markdown
## Description
Brief description of changes

## Type of Change
- [ ] Bug fix (non-breaking change that fixes an issue)
- [ ] New feature (non-breaking change that adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] Documentation update

## Testing
- [ ] Unit tests pass
- [ ] Integration tests pass
- [ ] Manual testing completed
- [ ] New tests added (if applicable)

## Checklist
- [ ] Code follows project style guidelines
- [ ] Self-review completed
- [ ] Documentation updated (if needed)
- [ ] No new warnings introduced
- [ ] Backward compatibility maintained

## Related Issues
Fixes #(issue number)
```

### 5. Review Process

1. **Automated checks** must pass (CI/CD pipeline)
2. **Code review** by at least one maintainer
3. **Testing** across supported databases
4. **Documentation review** for user-facing changes

## Coding Standards

### Java Code Style

**General Principles:**
- Follow [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html) with modifications
- Use meaningful variable and method names
- Keep methods focused and under 50 lines when possible
- Prefer composition over inheritance
- Use immutable objects where possible

**Specific Guidelines:**

```java
// ✅ Good: Descriptive naming
public List<UserAction> findUserActionsByTimeRange(Instant startTime, Instant endTime) {
    // Implementation
}

// ❌ Bad: Unclear naming
public List<UserAction> find(Instant s, Instant e) {
    // Implementation
}

// ✅ Good: Builder pattern for complex objects
UserAction userAction = UserAction.builder()
    .traceId(traceId)
    .userId(userId)
    .action("user_login")
    .build();

// ✅ Good: Null safety
public Optional<JobExecution> findJobExecutionByJobId(UUID jobId) {
    validateJobId(jobId);
    // Implementation
}
```

### Project Structure

```
src/
├── main/java/com/openrangelabs/tracer/
│   ├── annotation/          # Tracing annotations
│   ├── aspect/              # AOP aspects
│   ├── config/              # Configuration classes
│   ├── context/             # Thread-local context
│   ├── controller/          # REST endpoints
│   ├── event/               # Application events
│   ├── exception/           # Custom exceptions
│   ├── filter/              # Servlet filters
│   ├── metrics/             # Metrics collection
│   ├── model/               # Data models
│   │   └── converters/      # Database-specific converters
│   ├── repository/          # Data access layer
│   │   ├── jdbc/           # JDBC implementations
│   │   └── mongodb/        # MongoDB implementations
│   ├── resilience/          # Circuit breaker logic
│   ├── sanitization/        # Data sanitization
│   ├── service/             # Business logic
│   └── util/               # Utility classes
├── test/                    # Test code
└── resources/              # Configuration files
```

### Documentation Standards

**Javadoc Requirements:**
- All public classes and methods must have Javadoc
- Include `@param`, `@return`, and `@throws` where applicable
- Provide usage examples for complex APIs

```java
/**
 * Traces user actions with configurable parameters.
 * 
 * <p>Example usage:
 * <pre>{@code
 * @TraceUserAction(value = "user_search", measureTiming = true)
 * public List<Product> searchProducts(String query) {
 *     return productService.search(query);
 * }
 * }</pre>
 *
 * @param value the action name for identification
 * @param measureTiming whether to measure execution time
 * @return the traced method result
 * @throws TracingException if tracing configuration is invalid
 */
```

## Testing Guidelines

### Test Structure

```
src/test/java/
├── integration/           # Integration tests
│   ├── jdbc/             # JDBC database tests
│   └── mongodb/          # MongoDB tests
├── performance/          # Performance tests
└── unit/                 # Unit tests
```

### Test Categories

**Unit Tests:**
- Test individual methods and classes
- Mock external dependencies
- Fast execution (<100ms per test)

**Integration Tests:**
- Test database interactions
- Use Testcontainers for real databases
- Validate end-to-end scenarios

**Performance Tests:**
- Measure throughput and latency
- Validate memory usage
- Test under load

### Test Naming Convention

```java
// Pattern: methodName_condition_expectedResult
@Test
public void findUserActionsByTraceId_withValidTraceId_returnsUserActions() {
    // Test implementation
}

@Test
public void saveUserAction_withNullTraceId_throwsIllegalArgumentException() {
    // Test implementation
}
```

### Database Testing

**Use Testcontainers for integration tests:**

```java
@Testcontainers
class PostgreSqlIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("tracing_test")
            .withUsername("tracer")
            .withPassword("password");
    
    @Test
    void testUserActionRepository() {
        // Test implementation
    }
}
```

### Test Coverage

- **Minimum 80% line coverage** for new code
- **100% coverage** for critical paths (data persistence, security)
- **Edge case testing** for boundary conditions

## Documentation

### Types of Documentation

1. **API Documentation** - Javadoc for all public APIs
2. **User Guides** - README.md and wiki pages
3. **Configuration Reference** - Complete property documentation
4. **Examples** - Working code samples
5. **Migration Guides** - Version upgrade instructions

### Documentation Standards

- **Clear and concise** - Avoid jargon, explain concepts
- **Up-to-date** - Update docs with code changes
- **Tested examples** - All code examples must compile and run
- **Multiple formats** - Support different learning styles

### Example Documentation

```markdown
## Configuration Property

### `tracing.database.batch-size`

**Type:** `Integer`  
**Default:** `1000`  
**Range:** `1-10000`

Controls the number of records processed in a single batch operation.

**Example:**
```yaml
tracing:
  database:
    batch-size: 2000  # Process 2000 records per batch
```

**Performance Impact:**
- Higher values improve throughput but increase memory usage
- Lower values reduce memory usage but may impact performance
- Recommended range: 500-5000 depending on available memory
```

## Release Process

### Version Management

We follow [Semantic Versioning](https://semver.org/):

- **MAJOR** - Breaking changes
- **MINOR** - New features (backward compatible)
- **PATCH** - Bug fixes (backward compatible)

### Release Checklist

**Pre-Release:**
- [ ] All tests pass across all supported databases
- [ ] Documentation updated
- [ ] CHANGELOG.md updated
- [ ] Version numbers updated
- [ ] Migration guide prepared (for major/minor releases)

**Release:**
- [ ] Create release branch
- [ ] Final testing
- [ ] Tag release
- [ ] Build and publish artifacts
- [ ] Update GitHub release notes
- [ ] Announce release

### Backward Compatibility

**Breaking Changes Policy:**
- Breaking changes only in major versions
- Deprecation warnings in minor versions before removal
- Migration guides provided for all breaking changes
- Support for previous major version for 12 months

## Getting Help

### Communication Channels

- **GitHub Discussions** - General questions and discussions
- **GitHub Issues** - Bug reports and feature requests
- **Pull Request Reviews** - Code-specific discussions

### Maintainer Response Time

- **Bug reports** - Within 48 hours
- **Feature requests** - Within 1 week
- **Pull requests** - Within 1 week for initial review

### Finding Good First Issues

Look for issues labeled:
- `good first issue` - Suitable for newcomers
- `help wanted` - Community contribution welcome
- `documentation` - Documentation improvements needed

## Recognition

Contributors will be recognized in:
- **CONTRIBUTORS.md** file
- **Release notes** for significant contributions
- **Annual contributor highlights**

## Questions?

Don't hesitate to ask questions! We're here to help:

1. **Check existing documentation** first
2. **Search closed issues** for similar questions
3. **Create a GitHub Discussion** for general questions
4. **Create an issue** for specific problems

Thank you for contributing to the Spring Boot Tracer Library! 🎉