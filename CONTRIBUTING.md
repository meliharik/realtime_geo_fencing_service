# Contributing to Real-Time Geo-Fencing Engine

First off, thank you for considering contributing to this project! 🎉

## Code of Conduct

This project adheres to a code of conduct. By participating, you are expected to uphold this code. Please be respectful and constructive.

## How Can I Contribute?

### Reporting Bugs

Before creating bug reports, please check existing issues to avoid duplicates.

**When reporting a bug, please include:**
- A clear, descriptive title
- Steps to reproduce the issue
- Expected behavior vs. actual behavior
- Your environment (OS, Java version, Docker version)
- Relevant logs or screenshots

**Example:**
```markdown
**Bug:** Cache not warming up on startup

**Steps to Reproduce:**
1. Start docker-compose
2. Run `mvn spring-boot:run`
3. Check logs

**Expected:** Should see "Cache warm-up completed: X zones cached"
**Actual:** No cache warm-up message

**Environment:**
- OS: macOS 14.0
- Java: 17.0.8
- Docker: 24.0.6
```

### Suggesting Enhancements

Enhancement suggestions are tracked as GitHub issues.

**When suggesting an enhancement, please include:**
- A clear, descriptive title
- Detailed explanation of the proposed feature
- Why this enhancement would be useful
- Possible implementation approach (optional)

### Pull Requests

1. **Fork the repository** and create your branch from `main`
   ```bash
   git checkout -b feature/amazing-feature
   ```

2. **Make your changes**
   - Follow the code style guidelines below
   - Add tests for new functionality
   - Update documentation as needed

3. **Ensure tests pass**
   ```bash
   mvn clean test
   mvn verify
   ```

4. **Commit your changes**
   - Use clear, descriptive commit messages
   - Follow conventional commits format:
     ```
     feat: add WebSocket support for GPS streaming
     fix: resolve cache invalidation bug
     docs: update API documentation
     test: add integration tests for spatial queries
     refactor: optimize point-in-polygon algorithm
     ```

5. **Push to your fork**
   ```bash
   git push origin feature/amazing-feature
   ```

6. **Open a Pull Request**
   - Fill in the PR template
   - Link any related issues
   - Request review from maintainers

## Development Setup

### Prerequisites

- Java 17 JDK
- Maven 3.8+
- Docker & Docker Compose
- Git

### Setup Steps

```bash
# Clone your fork
git clone https://github.com/meliharik/realtime_geo_fencing_service.git
cd realtime-geo-fencing-service

# Add upstream remote
git remote add upstream https://github.com/meliharik/realtime_geo_fencing_service.git

# Start infrastructure
docker-compose up -d

# Run the application
mvn spring-boot:run

# Run tests
mvn test
```

## Code Style Guidelines

### Java

- Follow standard Java conventions
- Use meaningful variable and method names
- Add JavaDoc comments for public methods
- Keep methods focused (Single Responsibility Principle)
- Maximum line length: 120 characters

**Example:**
```java
/**
 * Checks if a GPS point violates any no-parking zones.
 *
 * @param gpsEvent The GPS event from a scooter
 * @return List of detected violations (empty if no violations)
 */
public List<ZoneViolationRecord> checkZoneViolation(GpsEventRecord gpsEvent) {
    // Implementation
}
```

### SQL

- Use uppercase for SQL keywords
- Use snake_case for table and column names
- Add comments for complex queries

**Example:**
```sql
-- Query to find zones containing a point
SELECT id, name
FROM no_parking_zones
WHERE active = TRUE
  AND ST_Contains(geometry, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326));
```

### Git Commit Messages

- Use present tense ("add feature" not "added feature")
- Use imperative mood ("move cursor to..." not "moves cursor to...")
- Limit first line to 72 characters
- Reference issues and PRs when relevant

**Good:**
```
feat: add rate limiting for violation alerts

Implements a 5-minute window to prevent duplicate
alerts for the same scooter/zone combination.

Closes #42
```

**Bad:**
```
fixed stuff
```

## Testing Guidelines

### Unit Tests

- Test business logic in isolation
- Mock external dependencies
- Aim for >80% code coverage

**Example:**
```java
@Test
void shouldDetectViolationWhenScooterEntersZone() {
    // Arrange
    GpsEventRecord event = new GpsEventRecord("SC-001", 37.78, -122.415, Instant.now(), null, null, null);

    // Act
    List<ZoneViolationRecord> violations = geoFencingService.checkZoneViolation(event);

    // Assert
    assertThat(violations).isNotEmpty();
    assertThat(violations.get(0).zoneName()).isEqualTo("Downtown SF Test Zone");
}
```

### Integration Tests

- Test with real database (Testcontainers)
- Test API endpoints end-to-end
- Test spatial queries with PostGIS

### Performance Tests

- Benchmark critical paths
- Document performance improvements
- Include before/after metrics in PR

## Documentation

- Update README.md for new features
- Add examples to TEST_SCENARIOS.md
- Document API changes
- Update QUICKSTART.md if setup changes

## Areas for Contribution

Looking for ideas? Here are some areas that need help:

### High Priority
- [ ] WebSocket support for real-time GPS streaming
- [ ] Admin dashboard with Leaflet.js map visualization
- [ ] Prometheus metrics export
- [ ] Load testing with JMeter

### Medium Priority
- [ ] GraphQL API
- [ ] Multi-tenancy support (multiple operators)
- [ ] Zone versioning (audit trail for zone changes)
- [ ] Geofence rules engine (custom alert conditions)

### Low Priority
- [ ] Mobile app example (Flutter/React Native)
- [ ] Kubernetes deployment manifests
- [ ] Terraform infrastructure as code
- [ ] Performance benchmarking suite

## Questions?

Feel free to:
- Open an issue with the `question` label
- Reach out to maintainers
- Join discussions in existing issues

## Recognition

Contributors will be recognized in:
- README.md acknowledgments section
- Release notes
- Contributors page

Thank you for contributing! 🙏
