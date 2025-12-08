package com.geofencing.engine.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.locationtech.jts.geom.Polygon;

import java.time.LocalDateTime;

/**
 * Entity representing a No-Parking Zone with spatial geometry.
 *
 * Design Rationale:
 * - Uses JTS Polygon type which Hibernate Spatial maps to PostGIS geometry
 * - SRID 4326 (WGS84) is the standard for GPS coordinates (latitude/longitude)
 * - The geometry field will be indexed with a GiST index for fast spatial queries
 * - 'active' flag allows zones to be disabled without deletion (soft delete pattern)
 *
 * Performance Note:
 * The geometry column should NEVER be eagerly fetched in list queries.
 * Use DTOs for listing zones and only fetch geometry when needed for spatial operations.
 */
@Entity
@Table(name = "no_parking_zones", indexes = {
    @Index(name = "idx_zone_active", columnList = "active"),
    @Index(name = "idx_zone_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NoParkingZone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Human-readable name for the zone (e.g., "Downtown Restricted Area")
     */
    @Column(nullable = false, length = 255)
    private String name;

    /**
     * Optional description of why this zone is restricted
     */
    @Column(length = 1000)
    private String description;

    /**
     * The polygon geometry defining the zone boundaries.
     *
     * CRITICAL: This uses PostGIS geometry type with SRID 4326.
     * - SRID 4326 = WGS84 coordinate system (standard GPS coordinates)
     * - columnDefinition ensures PostGIS understands the geometry type
     * - A GiST spatial index will be created on this column via migration script
     */
    @Column(name = "geometry", columnDefinition = "geometry(Polygon,4326)", nullable = false)
    private Polygon geometry;

    /**
     * Whether this zone is currently active.
     * Inactive zones are not evaluated during real-time processing.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * Optional severity level for violations (e.g., "HIGH", "MEDIUM", "LOW")
     * Can be used for prioritization or penalty calculation
     */
    @Column(length = 50)
    private String severity;

    /**
     * Timestamp when the zone was created
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when the zone was last updated
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Optional metadata in JSON format for extensibility
     * (e.g., operating hours, specific rules, etc.)
     */
    @Column(columnDefinition = "jsonb")
    private String metadata;

    /**
     * Calculates the area of this zone in square meters.
     * Uses the geometry's native area calculation.
     *
     * Note: For SRID 4326 (lat/lng), this returns degrees squared.
     * For accurate area, transform to a projected CRS (e.g., UTM).
     */
    public double getAreaInSquareMeters() {
        if (geometry != null) {
            // For production, you'd want to transform to a projected CRS
            // For now, this returns area in square degrees
            return geometry.getArea();
        }
        return 0.0;
    }

    /**
     * Returns the number of vertices in the polygon boundary.
     * Useful for complexity analysis and rendering optimization.
     */
    public int getVertexCount() {
        if (geometry != null) {
            return geometry.getNumPoints();
        }
        return 0;
    }
}
