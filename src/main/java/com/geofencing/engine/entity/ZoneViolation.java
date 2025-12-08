package com.geofencing.engine.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Entity representing a detected geo-fence violation.
 *
 * Design Decision - Why an Entity?
 * We use both the record DTO (ZoneViolationRecord) and this entity:
 * - Record: For in-memory processing and WebSocket transmission (immutable, fast)
 * - Entity: For persistence and audit trail (mutable, JPA-managed)
 *
 * Performance Strategy:
 * - Violations are batched in memory and persisted asynchronously
 * - Indexes optimize common queries (by scooter, by zone, by time)
 * - Denormalized fields avoid JOIN overhead in analytics queries
 */
@Entity
@Table(
    name = "zone_violations",
    indexes = {
        @Index(name = "idx_violation_scooter", columnList = "scooter_id"),
        @Index(name = "idx_violation_zone", columnList = "zone_id"),
        @Index(name = "idx_violation_timestamp", columnList = "timestamp"),
        @Index(name = "idx_violation_scooter_time", columnList = "scooter_id, timestamp")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZoneViolation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique identifier for this violation event.
     * Format: {scooterId}_{epochMillis}
     */
    @Column(name = "violation_id", nullable = false, unique = true)
    private String violationId;

    /**
     * Scooter identifier (denormalized for fast queries)
     */
    @Column(name = "scooter_id", nullable = false, length = 100)
    private String scooterId;

    /**
     * Zone ID (foreign key, but denormalized zone name for performance)
     */
    @Column(name = "zone_id", nullable = false)
    private Long zoneId;

    /**
     * Zone name (denormalized to avoid JOINs in analytics)
     */
    @Column(name = "zone_name", nullable = false)
    private String zoneName;

    /**
     * GPS coordinates where violation occurred
     */
    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    /**
     * Timestamp when the GPS event triggered this violation
     */
    @Column(nullable = false)
    private Instant timestamp;

    /**
     * Severity level from the zone configuration
     */
    @Column(length = 50)
    private String severity;

    /**
     * Optional: Distance from scooter to zone center (meters)
     */
    @Column(name = "distance_to_center")
    private Double distanceToCenter;

    /**
     * When this record was created in the database
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Optional: Reference to the zone entity
     * Using LAZY loading to avoid unnecessary joins
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", insertable = false, updatable = false)
    private NoParkingZone zone;
}
