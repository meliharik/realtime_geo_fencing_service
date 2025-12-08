package com.geofencing.engine.controller;

import com.geofencing.engine.dto.GpsEventRecord;
import com.geofencing.engine.dto.ZoneViolationRecord;
import com.geofencing.engine.entity.NoParkingZone;
import com.geofencing.engine.service.GeoFencingService;
import com.geofencing.engine.service.ZoneCacheService;
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
    @PostMapping("/check")
    public ResponseEntity<?> checkViolation(@RequestBody GpsEventRecord gpsEvent) {
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
    @GetMapping("/check-quick")
    public ResponseEntity<?> checkViolationQuick(
        @RequestParam String scooterId,
        @RequestParam double lat,
        @RequestParam double lon
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
