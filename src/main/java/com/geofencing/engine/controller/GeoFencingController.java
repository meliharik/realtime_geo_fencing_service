package com.geofencing.engine.controller;

import com.geofencing.engine.dto.GpsEventRecord;
import com.geofencing.engine.dto.ZoneViolationRecord;
import com.geofencing.engine.entity.NoParkingZone;
import com.geofencing.engine.service.GeoFencingService;
import com.geofencing.engine.service.ZoneCacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for testing geo-fencing functionality.
 *
 * This controller provides endpoints to:
 * 1. Send test GPS events
 * 2. View all zones
 * 3. Check cache status
 * 4. View violation history
 *
 * Use this for manual testing before implementing WebSocket.
 */
@RestController
@RequestMapping("/api/geofencing")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Geo-Fencing", description = "Real-time geo-fence violation detection API")
public class GeoFencingController {

    private final GeoFencingService geoFencingService;
    private final ZoneCacheService zoneCacheService;

    /**
     * Test endpoint: Send a GPS event and check for violations.
     *
     * Example request:
     * POST /api/geofencing/check
     * {
     *   "scooterId": "SC-TEST-001",
     *   "latitude": 37.7800,
     *   "longitude": -122.4150,
     *   "timestamp": 1234567890000
     * }
     *
     * If the point is inside the sample zone, you'll get a violation response!
     */
    @Operation(
            summary = "Check GPS location for violations",
            description = "Checks if a GPS coordinate violates any no-parking zones. " +
                    "Uses Redis cache for fast lookups (~0.1-0.5ms) with PostGIS fallback (~5-10ms)."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully checked location",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "No violation",
                                            value = "{\"status\":\"OK\",\"message\":\"No violations detected\",\"scooterId\":\"SC-001\",\"location\":{\"latitude\":37.78,\"longitude\":-122.415}}"
                                    ),
                                    @ExampleObject(
                                            name = "Violation detected",
                                            value = "{\"status\":\"VIOLATION\",\"message\":\"Zone violation detected!\",\"scooterId\":\"SC-001\",\"violations\":[{\"violationId\":\"VIO-123\",\"scooterId\":\"SC-001\",\"zoneId\":1,\"zoneName\":\"Downtown SF\",\"latitude\":37.78,\"longitude\":-122.415,\"timestamp\":\"2024-01-01T12:00:00Z\",\"severity\":\"HIGH\",\"distanceFromBoundary\":0.0}]}"
                                    )
                            }
                    )
            )
    })
    @PostMapping("/check")
    public ResponseEntity<?> checkViolation(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "GPS event with scooter ID, coordinates, and timestamp",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = GpsEventRecord.class),
                            examples = @ExampleObject(
                                    value = "{\"scooterId\":\"SC-001\",\"latitude\":37.7800,\"longitude\":-122.4150,\"timestamp\":\"2024-01-01T12:00:00Z\"}"
                            )
                    )
            )
            @RequestBody GpsEventRecord gpsEvent) {
        log.info("Received GPS event: {}", gpsEvent.toLogString());

        List<ZoneViolationRecord> violations = geoFencingService.checkZoneViolation(gpsEvent);

        if (violations.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                "status", "OK",
                "message", "No violations detected",
                "scooterId", gpsEvent.scooterId(),
                "location", Map.of(
                    "latitude", gpsEvent.latitude(),
                    "longitude", gpsEvent.longitude()
                )
            ));
        } else {
            return ResponseEntity.ok(Map.of(
                "status", "VIOLATION",
                "message", "Zone violation detected!",
                "scooterId", gpsEvent.scooterId(),
                "violations", violations
            ));
        }
    }

    /**
     * Quick test endpoint with query parameters (easier to test in browser).
     *
     * Example:
     * GET /api/geofencing/check-quick?scooterId=SC-001&lat=37.7800&lon=-122.4150
     */
    @Operation(
            summary = "Quick violation check with query params",
            description = "Simplified endpoint for browser testing. Use query parameters instead of JSON body."
    )
    @GetMapping("/check-quick")
    public ResponseEntity<?> checkViolationQuick(
        @Parameter(description = "Scooter ID", example = "SC-001") @RequestParam String scooterId,
        @Parameter(description = "Latitude", example = "37.7800") @RequestParam double lat,
        @Parameter(description = "Longitude", example = "-122.4150") @RequestParam double lon
    ) {
        GpsEventRecord event = new GpsEventRecord(
            scooterId,
            lat,
            lon,
            Instant.now(),
            null,
            null,
            null
        );

        return checkViolation(event);
    }

    /**
     * Get all active zones.
     *
     * Example:
     * GET /api/geofencing/zones
     */
    @Operation(
            summary = "Get all active no-parking zones",
            description = "Returns a list of all active geo-fence zones from the database."
    )
    @GetMapping("/zones")
    public ResponseEntity<List<NoParkingZone>> getAllZones() {
        List<NoParkingZone> zones = geoFencingService.getAllActiveZones();
        return ResponseEntity.ok(zones);
    }

    /**
     * Get cache statistics.
     *
     * Example:
     * GET /api/geofencing/cache/stats
     */
    @Operation(
            summary = "Get Redis cache statistics",
            description = "Returns cache health metrics including cached zone count and hit rate."
    )
    @GetMapping("/cache/stats")
    public ResponseEntity<?> getCacheStats() {
        ZoneCacheService.CacheStats stats = zoneCacheService.getCacheStats();

        return ResponseEntity.ok(Map.of(
            "cachedZoneCount", stats.cachedZoneCount(),
            "databaseZoneCount", stats.databaseZoneCount(),
            "cacheHealthy", stats.isCacheHealthy(),
            "cacheHitRate", String.format("%.1f%%", stats.cacheHitRate())
        ));
    }

    /**
     * Manually refresh the cache.
     *
     * Example:
     * POST /api/geofencing/cache/refresh
     */
    @PostMapping("/cache/refresh")
    public ResponseEntity<?> refreshCache() {
        int refreshedCount = zoneCacheService.refreshAllZones();
        return ResponseEntity.ok(Map.of(
            "status", "SUCCESS",
            "message", "Cache refreshed",
            "zonesRefreshed", refreshedCount
        ));
    }

    /**
     * Get violation history for a scooter.
     *
     * Example:
     * GET /api/geofencing/violations/SC-001
     */
    @GetMapping("/violations/{scooterId}")
    public ResponseEntity<?> getViolationHistory(@PathVariable String scooterId) {
        return ResponseEntity.ok(geoFencingService.getScooterViolationHistory(scooterId));
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "Real-Time Geo-Fencing Engine",
            "timestamp", Instant.now()
        ));
    }
}
