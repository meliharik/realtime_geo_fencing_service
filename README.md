# Real-Time Geo-Fencing Engine

A high-performance, event-driven geo-fencing service built with Java 17 and Spring Boot 3 for processing GPS data streams from scooter fleets.

## Architecture Overview

This system detects when scooters enter predefined "No-Parking Zones" in real-time by processing high-frequency GPS streams.

### Tech Stack
- **Java 17 LTS** - Language
- **Spring Boot 3.2.5** - Framework
- **PostgreSQL 16 + PostGIS 3.4** - Spatial database
- **Redis 7.2** - Geometry caching
- **Hibernate Spatial** - JPA spatial support
- **JTS (Java Topology Suite)** - Computational geometry
- **Flyway** - Database migrations

## Project Status

### ✅ Phase 1: Infrastructure (Complete)
- Docker Compose with PostgreSQL (PostGIS) and Redis
- Maven POM with Java 17 dependencies
- Health checks and volume persistence

### ✅ Phase 2: Domain Model & Database Schema (Complete)
- `NoParkingZone` entity with PostGIS geometry support
- `GpsEventRecord` immutable DTO (Java 17 record)
- `ZoneViolationRecord` output DTO (Java 17 record)
- Database migration with spatial indexes (GiST)
- Application configuration

## Quick Start

### Prerequisites
- Docker & Docker Compose
- Java 17 JDK
- Maven 3.8+

### 1. Start Infrastructure
```bash
docker-compose up -d
```

### 2. Verify PostgreSQL + PostGIS
```bash
docker exec -it geo-fencing-postgres psql -U geofencing_user -d geofencing -c "SELECT PostGIS_Version();"
```

### 3. Verify Redis
```bash
docker exec -it geo-fencing-redis redis-cli ping
```

### 4. Build Application
```bash
mvn clean install
```

### 5. Run Migrations
Migrations will run automatically on application startup via Flyway.

### 6. Start Application
```bash
mvn spring-boot:run
```

## Key Design Decisions

### Why PostGIS?
- **Native Spatial Indexing**: GiST indexes make point-in-polygon queries 100-1000x faster
- **Industry Standard**: Proven at scale (Uber, Lyft use PostGIS for geo-queries)
- **Rich Spatial Functions**: ST_Contains, ST_DWithin, ST_Distance built-in

### Why Redis Caching?
- **Performance**: Querying PostgreSQL for every GPS point doesn't scale
- **Strategy**: Cache active zone geometries in Redis for microsecond lookups
- **LRU Eviction**: Automatically manages memory with `allkeys-lru` policy

### Why Java 17 Records?
- **Immutability**: GPS events are facts that shouldn't change
- **Thread-Safety**: Critical for high-concurrency processing
- **Efficiency**: Optimized hashCode/equals for deduplication

### Why GiST Index?
Without GiST index:
- Every GPS check scans ALL polygons: **O(n × m)** complexity
- For 1000 zones and 10,000 GPS points/sec = 10M operations/sec

With GiST index:
- Bounding box acceleration: **O(log n)** complexity
- Same workload = ~100K operations/sec (100x improvement)

## Database Schema

### `no_parking_zones` Table
- `id` - Primary key
- `name` - Human-readable zone name
- `geometry` - PostGIS Polygon (SRID 4326 = WGS84)
- `active` - Enable/disable zones without deletion
- `severity` - LOW, MEDIUM, HIGH
- Spatial index: `idx_no_parking_zones_geometry` (GiST)

### `zone_violations` Table
- `id` - Primary key
- `scooter_id` - Denormalized for fast queries
- `zone_id` - Foreign key to no_parking_zones
- `latitude`, `longitude` - Violation location
- `timestamp` - When violation occurred
- Indexes optimized for scooter and time-range queries

## Next Steps

### Phase 3: Repository & Service Layer (Pending)
- Spring Data JPA repository with spatial queries
- Zone caching service using Redis
- Geo-fence detection engine

### Phase 4: WebSocket & Event Processing (Pending)
- WebSocket endpoint for GPS streams
- Async event processing
- Violation broadcasting

### Phase 5: Performance & Monitoring (Pending)
- Load testing with JMeter
- Prometheus metrics
- Performance tuning

## Configuration

See `src/main/resources/application.yml` for:
- Database connection settings
- Redis configuration
- HikariCP pool sizing
- Logging levels

## Sample Data

The migration script includes a sample no-parking zone in San Francisco:
- **Location**: Downtown SF (37.7749, -122.4194)
- **Type**: Rectangular polygon
- **Severity**: HIGH

Use this for testing geo-fence detection.

## Development

### Adding a New Zone
```sql
INSERT INTO no_parking_zones (name, description, geometry, active, severity)
VALUES (
    'Test Zone',
    'Description',
    ST_GeomFromText('POLYGON((...coordinates...))', 4326),
    TRUE,
    'MEDIUM'
);
```

### Testing Point-in-Polygon
```sql
SELECT id, name
FROM no_parking_zones
WHERE active = TRUE
  AND ST_Contains(geometry, ST_SetSRID(ST_MakePoint(-122.4150, 37.7800), 4326));
```

## Performance Considerations

1. **Connection Pooling**: HikariCP configured with 20 max connections
2. **Batch Processing**: Hibernate batch size = 50
3. **Redis Pool**: 50 max active connections
4. **Spatial Index**: GiST index on geometry column
5. **Denormalization**: Violation table has denormalized zone data to avoid joins

## License

MIT
