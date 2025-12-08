-- ============================================================================
-- Migration V1: Initial Schema for Real-Time Geo-Fencing Engine
-- ============================================================================
-- This migration creates the core tables with spatial support.
--
-- CRITICAL PERFORMANCE DECISIONS:
-- 1. PostGIS Extension: Enables spatial data types and functions
-- 2. GiST Index: Generalized Search Tree index for spatial queries
--    - Without this, point-in-polygon queries would be O(n) scans
--    - With GiST, queries become O(log n) with bounding box acceleration
-- 3. SRID 4326: WGS84 coordinate system (standard GPS lat/lng)
-- ============================================================================

-- Enable PostGIS extension (idempotent)
CREATE EXTENSION IF NOT EXISTS postgis;

-- Verify PostGIS version (useful for debugging)
-- SELECT PostGIS_Version();

-- ============================================================================
-- Table: no_parking_zones
-- ============================================================================
-- Stores polygon geometries defining restricted parking areas.
--
-- Performance Notes:
-- - The geometry column uses the GEOMETRY type with explicit SRID
-- - A GiST spatial index is created for fast spatial queries
-- - Active zones are indexed separately for quick filtering
-- - JSONB column allows flexible metadata without schema changes
-- ============================================================================

CREATE TABLE no_parking_zones (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),

    -- Spatial column: stores polygon geometry with SRID 4326 (WGS84)
    -- SRID 4326 = latitude/longitude coordinate system used by GPS
    geometry GEOMETRY(Polygon, 4326) NOT NULL,

    -- Business fields
    active BOOLEAN NOT NULL DEFAULT TRUE,
    severity VARCHAR(50),

    -- Audit timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Extensibility: store additional metadata as JSON
    metadata JSONB,

    -- Constraints
    CONSTRAINT chk_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH') OR severity IS NULL)
);

-- ============================================================================
-- Indexes for no_parking_zones
-- ============================================================================

-- Spatial index (GiST) - THE MOST CRITICAL INDEX FOR PERFORMANCE
-- This enables fast point-in-polygon queries via bounding box acceleration
-- Without this, every GPS check would scan all polygons = O(n * m) complexity
CREATE INDEX idx_no_parking_zones_geometry ON no_parking_zones USING GIST(geometry);

-- Filter active zones quickly (most queries only check active zones)
CREATE INDEX idx_no_parking_zones_active ON no_parking_zones(active) WHERE active = TRUE;

-- Audit/sorting by creation time
CREATE INDEX idx_no_parking_zones_created_at ON no_parking_zones(created_at);

-- JSONB index for metadata queries (if you store searchable data in metadata)
CREATE INDEX idx_no_parking_zones_metadata ON no_parking_zones USING GIN(metadata);

-- ============================================================================
-- Table: zone_violations (Optional - for audit trail)
-- ============================================================================
-- Stores detected violations for analytics and compliance.
--
-- Design Note:
-- This table is optional for Phase 2. In high-throughput systems, you might:
-- 1. Buffer violations in Redis and batch-write to DB
-- 2. Stream violations to a time-series DB (InfluxDB, TimescaleDB)
-- 3. Send violations to a message queue (Kafka) for downstream processing
--
-- For now, we'll create a simple table for MVP purposes.
-- ============================================================================

CREATE TABLE zone_violations (
    id BIGSERIAL PRIMARY KEY,
    violation_id VARCHAR(255) NOT NULL UNIQUE,

    -- Denormalized data for fast queries (avoid joins)
    scooter_id VARCHAR(100) NOT NULL,
    zone_id BIGINT NOT NULL,
    zone_name VARCHAR(255) NOT NULL,

    -- Location where violation occurred
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,

    -- Timestamp of the GPS event that triggered the violation
    timestamp TIMESTAMP NOT NULL,

    -- Severity level (denormalized from zone)
    severity VARCHAR(50),

    -- Optional: distance from scooter to zone center
    distance_to_center DOUBLE PRECISION,

    -- Audit
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Foreign key (optional - can be removed for performance)
    CONSTRAINT fk_zone FOREIGN KEY (zone_id) REFERENCES no_parking_zones(id) ON DELETE CASCADE
);

-- ============================================================================
-- Indexes for zone_violations
-- ============================================================================

-- Fast lookups by scooter (e.g., "show all violations for scooter X")
CREATE INDEX idx_zone_violations_scooter ON zone_violations(scooter_id);

-- Fast lookups by zone (e.g., "show all violations in zone Y")
CREATE INDEX idx_zone_violations_zone ON zone_violations(zone_id);

-- Time-range queries (e.g., "violations in the last hour")
CREATE INDEX idx_zone_violations_timestamp ON zone_violations(timestamp DESC);

-- Composite index for common queries (scooter + time range)
CREATE INDEX idx_zone_violations_scooter_time ON zone_violations(scooter_id, timestamp DESC);

-- ============================================================================
-- Function: Update updated_at timestamp automatically
-- ============================================================================
-- Trigger to automatically update the updated_at column on row modification.
-- ============================================================================

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Attach trigger to no_parking_zones table
CREATE TRIGGER trigger_update_no_parking_zones_updated_at
    BEFORE UPDATE ON no_parking_zones
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ============================================================================
-- Sample Data (Optional - for testing)
-- ============================================================================
-- Insert a sample no-parking zone in San Francisco downtown area.
-- This is a simple rectangular polygon for testing purposes.
-- ============================================================================

INSERT INTO no_parking_zones (name, description, geometry, active, severity)
VALUES (
    'Downtown SF Test Zone',
    'Sample no-parking zone for testing geo-fence detection',
    ST_GeomFromText(
        'POLYGON((-122.4194 37.7749, -122.4194 37.7849, -122.4094 37.7849, -122.4094 37.7749, -122.4194 37.7749))',
        4326
    ),
    TRUE,
    'HIGH'
);

-- Verify the insert worked and calculate area
-- SELECT
--     id,
--     name,
--     ST_AsText(geometry) as wkt,
--     ST_Area(geometry) as area_sq_degrees,
--     active,
--     severity
-- FROM no_parking_zones;

-- ============================================================================
-- Useful Spatial Queries (commented out - for reference)
-- ============================================================================

-- Check if a point is inside a zone (basic geo-fence check)
-- SELECT id, name
-- FROM no_parking_zones
-- WHERE active = TRUE
--   AND ST_Contains(geometry, ST_SetSRID(ST_MakePoint(-122.4150, 37.7800), 4326));

-- Find all zones within 1km of a point
-- SELECT id, name, ST_Distance(geometry::geography, ST_SetSRID(ST_MakePoint(-122.4150, 37.7800), 4326)::geography) as distance_meters
-- FROM no_parking_zones
-- WHERE active = TRUE
--   AND ST_DWithin(geometry::geography, ST_SetSRID(ST_MakePoint(-122.4150, 37.7800), 4326)::geography, 1000)
-- ORDER BY distance_meters;

-- ============================================================================
-- End of Migration V1
-- ============================================================================
