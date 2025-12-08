package com.geofencing.engine.dto;

import java.time.Instant;

/**
 * Immutable record representing a detected geo-fence violation.
 *
 * This is the output of the geo-fencing engine when a scooter enters a no-parking zone.
 *
 * Design Rationale:
 * - Immutable for thread-safe event processing
 * - Will be sent via WebSocket to monitoring dashboards
 * - Can be persisted to a violations table for audit/analytics
 * - Can trigger downstream actions (alerts, scooter lockdown, etc.)
 *
 * @param violationId      Unique identifier for this violation event
 * @param scooterId        The scooter that violated the zone
 * @param zoneId           The no-parking zone that was violated
 * @param zoneName         Human-readable zone name
 * @param latitude         GPS latitude where violation occurred
 * @param longitude        GPS longitude where violation occurred
 * @param timestamp        When the violation was detected
 * @param severity         Severity level from the zone configuration
 * @param distanceToCenter Distance from scooter to zone center in meters (optional)
 */
public record ZoneViolationRecord(
    String violationId,
    String scooterId,
    Long zoneId,
    String zoneName,
    Double latitude,
    Double longitude,
    Instant timestamp,
    String severity,
    Double distanceToCenter
) {

    /**
     * Factory method for creating violations from GPS events and zone data.
     */
    public static ZoneViolationRecord fromGpsEvent(
        GpsEventRecord gpsEvent,
        Long zoneId,
        String zoneName,
        String severity
    ) {
        String violationId = generateViolationId(gpsEvent.scooterId(), gpsEvent.timestamp());

        return new ZoneViolationRecord(
            violationId,
            gpsEvent.scooterId(),
            zoneId,
            zoneName,
            gpsEvent.latitude(),
            gpsEvent.longitude(),
            gpsEvent.timestamp(),
            severity,
            null // Distance calculation can be added later if needed
        );
    }

    /**
     * Generates a unique violation ID from scooter ID and timestamp.
     * Format: {scooterId}_{epochMillis}
     */
    private static String generateViolationId(String scooterId, Instant timestamp) {
        return String.format("%s_%d", scooterId, timestamp.toEpochMilli());
    }

    /**
     * Compact string for logging violations.
     */
    public String toLogString() {
        return String.format(
            "Violation[id=%s, scooter=%s, zone=%s, severity=%s]",
            violationId, scooterId, zoneName, severity
        );
    }

    /**
     * Checks if this violation is critical (HIGH severity).
     */
    public boolean isCritical() {
        return "HIGH".equalsIgnoreCase(severity);
    }
}
