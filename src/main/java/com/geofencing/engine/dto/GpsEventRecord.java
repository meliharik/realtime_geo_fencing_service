package com.geofencing.engine.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;

import java.time.Instant;

/**
 * Immutable DTO representing a GPS event from a scooter.
 *
 * Design Rationale - Why a Record?
 * 1. Immutability: GPS events are facts that happened - they should never change
 * 2. Efficiency: Records generate optimized hashCode/equals for caching and deduplication
 * 3. Thread-Safety: Immutable objects are inherently thread-safe (critical for high concurrency)
 * 4. Pattern Matching: Java 17 pattern matching works beautifully with records
 *
 * Performance Consideration:
 * This record will be created millions of times per day. The compact constructor
 * validates data once at creation time, preventing invalid events from entering the system.
 *
 * @param scooterId   Unique identifier of the scooter
 * @param latitude    GPS latitude in decimal degrees (WGS84)
 * @param longitude   GPS longitude in decimal degrees (WGS84)
 * @param timestamp   When the GPS reading was captured (epoch milliseconds)
 * @param speed       Optional speed in km/h
 * @param heading     Optional direction in degrees (0-360, where 0 is North)
 * @param accuracy    Optional GPS accuracy in meters
 */
public record GpsEventRecord(
    @NotBlank(message = "Scooter ID cannot be blank")
    String scooterId,

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0", message = "Latitude must be >= -90")
    @DecimalMax(value = "90.0", message = "Latitude must be <= 90")
    Double latitude,

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be >= -180")
    @DecimalMax(value = "180.0", message = "Longitude must be <= 180")
    Double longitude,

    @NotNull(message = "Timestamp is required")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    Instant timestamp,

    @PositiveOrZero(message = "Speed must be >= 0")
    Double speed,

    @Min(value = 0, message = "Heading must be >= 0")
    @Max(value = 360, message = "Heading must be <= 360")
    Double heading,

    @PositiveOrZero(message = "Accuracy must be >= 0")
    Double accuracy
) {

    /**
     * Compact constructor with validation.
     * This runs before the canonical constructor and allows validation/normalization.
     */
    public GpsEventRecord {
        // Normalize heading to 0-360 range if provided
        if (heading != null && heading > 360) {
            heading = heading % 360;
        }

        // Clamp accuracy to reasonable bounds (GPS accuracy > 100m is usually unreliable)
        if (accuracy != null && accuracy > 100.0) {
            accuracy = 100.0;
        }

        // Ensure timestamp is not in the future (with 1-minute tolerance for clock skew)
        if (timestamp != null && timestamp.isAfter(Instant.now().plusSeconds(60))) {
            throw new IllegalArgumentException("GPS timestamp cannot be in the future");
        }
    }

    /**
     * Factory method for creating events from raw WebSocket messages.
     * This provides a clear API and encapsulates construction logic.
     */
    public static GpsEventRecord fromWebSocketMessage(
        String scooterId,
        double lat,
        double lon,
        long timestampMillis
    ) {
        return new GpsEventRecord(
            scooterId,
            lat,
            lon,
            Instant.ofEpochMilli(timestampMillis),
            null,
            null,
            null
        );
    }

    /**
     * Checks if this GPS reading is recent (within last N seconds).
     * Used to filter out stale events in high-throughput scenarios.
     */
    public boolean isRecent(int maxAgeSeconds) {
        return timestamp.isAfter(Instant.now().minusSeconds(maxAgeSeconds));
    }

    /**
     * Checks if the GPS accuracy is acceptable for geo-fencing.
     * Events with poor accuracy should be filtered or flagged.
     */
    public boolean hasAcceptableAccuracy(double maxAccuracyMeters) {
        return accuracy == null || accuracy <= maxAccuracyMeters;
    }

    /**
     * Returns a compact string representation for logging.
     * Avoids logging sensitive data in production.
     */
    public String toLogString() {
        return String.format(
            "GpsEvent[scooter=%s, lat=%.6f, lon=%.6f, time=%s]",
            scooterId, latitude, longitude, timestamp
        );
    }
}
