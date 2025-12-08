package com.geofencing.engine.service;

import com.geofencing.engine.dto.CachedZoneRecord;
import com.geofencing.engine.entity.NoParkingZone;
import com.geofencing.engine.repository.NoParkingZoneRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.WKTWriter;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Service for caching zone geometries in Redis.
 *
 * This is the PERFORMANCE MULTIPLIER of the entire system.
 *
 * Without this service:
 * - Every GPS event queries PostgreSQL: ~5ms
 * - 10,000 events/sec = 50,000ms = 50 seconds of DB time
 * - Result: System can't keep up with real-time load
 *
 * With this service:
 * - Zones cached in Redis: ~0.1ms per check
 * - 10,000 events/sec = 1,000ms = 1 second of Redis time
 * - Result: 50x performance improvement!
 *
 * Architecture:
 * 1. Cache Warming: Load all zones on startup
 * 2. Cache Format: Store as WKT strings in Redis
 * 3. Cache Refresh: Scheduled task every 30 minutes
 * 4. Cache Invalidation: Manual trigger when zones change
 *
 * Interview Talking Point:
 * "This demonstrates understanding of caching strategies, distributed systems,
 * and the CAP theorem. We accept eventual consistency (zones updated in DB
 * aren't immediately reflected in cache) for massive performance gains."
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ZoneCacheService {

    private final NoParkingZoneRepository zoneRepository;
    private final RedisTemplate<String, String> stringRedisTemplate;

    // Redis key prefix for zone geometries
    private static final String ZONE_KEY_PREFIX = "zone:geometry:";
    private static final String ZONE_METADATA_PREFIX = "zone:metadata:";
    private static final String ALL_ZONES_KEY = "zones:active";

    // TTL for cached zones (60 minutes)
    private static final long CACHE_TTL_MINUTES = 60;

    // JTS readers/writers for WKT conversion
    private final WKTReader wktReader = new WKTReader();
    private final WKTWriter wktWriter = new WKTWriter();

    /**
     * Cache warming on application startup.
     *
     * This ensures the cache is ready before the application starts
     * accepting GPS events.
     *
     * Startup Flow:
     * 1. Spring Boot starts
     * 2. Beans are created
     * 3. @PostConstruct methods run
     * 4. Cache is warmed with all active zones
     * 5. Application is ready to handle traffic
     */
    @PostConstruct
    public void warmUpCache() {
        log.info("Starting cache warm-up...");
        long startTime = System.currentTimeMillis();

        try {
            int cachedCount = refreshAllZones();
            long duration = System.currentTimeMillis() - startTime;

            log.info("Cache warm-up completed: {} zones cached in {}ms",
                cachedCount, duration);
        } catch (Exception e) {
            log.error("Cache warm-up failed", e);
            // Don't throw - let the application start even if cache warming fails
            // The system will fall back to database queries
        }
    }

    /**
     * Scheduled cache refresh every 30 minutes.
     *
     * Why scheduled refresh?
     * - Zones can be updated in the database by admins
     * - Without refresh, cache would be stale
     * - 30 minutes is a balance between freshness and load
     *
     * Alternative: Event-driven invalidation (more complex but more accurate)
     */
    @Scheduled(fixedRateString = "${geofencing.cache.zones.refresh-interval-minutes:30}",
               timeUnit = TimeUnit.MINUTES,
               initialDelay = 30)
    public void scheduledCacheRefresh() {
        log.info("Starting scheduled cache refresh...");
        try {
            int refreshedCount = refreshAllZones();
            log.info("Scheduled cache refresh completed: {} zones refreshed", refreshedCount);
        } catch (Exception e) {
            log.error("Scheduled cache refresh failed", e);
        }
    }

    /**
     * Refreshes all active zones in the cache.
     *
     * Strategy:
     * 1. Fetch all active zones from database
     * 2. Convert geometries to WKT format
     * 3. Store in Redis with TTL
     * 4. Maintain a set of active zone IDs for quick lookup
     *
     * @return Number of zones cached
     */
    public int refreshAllZones() {
        List<NoParkingZone> activeZones = zoneRepository.findByActiveTrue();

        if (activeZones.isEmpty()) {
            log.warn("No active zones found in database");
            return 0;
        }

        int cachedCount = 0;
        List<String> zoneIds = new ArrayList<>();

        for (NoParkingZone zone : activeZones) {
            try {
                cacheZone(zone);
                zoneIds.add(String.valueOf(zone.getId()));
                cachedCount++;
            } catch (Exception e) {
                log.error("Failed to cache zone: {}", zone.getId(), e);
            }
        }

        // Store list of active zone IDs for quick iteration
        stringRedisTemplate.delete(ALL_ZONES_KEY);
        if (!zoneIds.isEmpty()) {
            stringRedisTemplate.opsForSet().add(ALL_ZONES_KEY, zoneIds.toArray(new String[0]));
            stringRedisTemplate.expire(ALL_ZONES_KEY, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        }

        log.info("Cached {} zones in Redis", cachedCount);
        return cachedCount;
    }

    /**
     * Caches a single zone in Redis.
     *
     * Redis Storage Format:
     * - Key: "zone:geometry:{zoneId}"
     * - Value: WKT string (e.g., "POLYGON((-122.41 37.77, ...))")
     * - Key: "zone:metadata:{zoneId}"
     * - Value: JSON with name, severity, etc.
     */
    private void cacheZone(NoParkingZone zone) {
        if (zone.getGeometry() == null) {
            log.warn("Zone {} has null geometry, skipping cache", zone.getId());
            return;
        }

        // Convert JTS Polygon to WKT string
        String wkt = wktWriter.write(zone.getGeometry());

        // Store geometry
        String geometryKey = ZONE_KEY_PREFIX + zone.getId();
        stringRedisTemplate.opsForValue().set(geometryKey, wkt, CACHE_TTL_MINUTES, TimeUnit.MINUTES);

        // Store metadata as JSON string
        String metadataKey = ZONE_METADATA_PREFIX + zone.getId();
        String metadata = String.format(
            "{\"id\":%d,\"name\":\"%s\",\"severity\":\"%s\"}",
            zone.getId(), zone.getName(), zone.getSeverity()
        );
        stringRedisTemplate.opsForValue().set(metadataKey, metadata, CACHE_TTL_MINUTES, TimeUnit.MINUTES);

        log.debug("Cached zone: id={}, name={}", zone.getId(), zone.getName());
    }

    /**
     * Gets all cached zones as CachedZoneRecord objects.
     *
     * This is called by GeoFencingService for each GPS event.
     *
     * Performance:
     * - Redis GET for all zone IDs: ~1ms
     * - Redis MGET for all geometries: ~2ms
     * - WKT parsing to JTS Polygon: ~1ms per zone
     * - Total for 1000 zones: ~20ms (one-time cost per check)
     *
     * Optimization Opportunity:
     * For even better performance, we could keep zones in application memory
     * and only use Redis for persistence/sharing across instances.
     */
    public List<CachedZoneRecord> getAllCachedZones() {
        // Get list of active zone IDs
        Set<String> zoneIds = stringRedisTemplate.opsForSet().members(ALL_ZONES_KEY);

        if (zoneIds == null || zoneIds.isEmpty()) {
            log.warn("No zones found in cache, falling back to database");
            refreshAllZones();
            zoneIds = stringRedisTemplate.opsForSet().members(ALL_ZONES_KEY);
        }

        List<CachedZoneRecord> cachedZones = new ArrayList<>();

        for (String zoneIdStr : zoneIds) {
            try {
                Long zoneId = Long.parseLong(zoneIdStr);
                CachedZoneRecord cachedZone = getCachedZone(zoneId);
                if (cachedZone != null) {
                    cachedZones.add(cachedZone);
                }
            } catch (Exception e) {
                log.error("Failed to retrieve cached zone: {}", zoneIdStr, e);
            }
        }

        return cachedZones;
    }

    /**
     * Gets a single cached zone by ID.
     */
    public CachedZoneRecord getCachedZone(Long zoneId) {
        try {
            // Get geometry WKT
            String geometryKey = ZONE_KEY_PREFIX + zoneId;
            String wkt = stringRedisTemplate.opsForValue().get(geometryKey);

            if (wkt == null) {
                log.debug("Zone {} not found in cache", zoneId);
                return null;
            }

            // Get metadata
            String metadataKey = ZONE_METADATA_PREFIX + zoneId;
            String metadata = stringRedisTemplate.opsForValue().get(metadataKey);

            // Parse WKT to JTS Polygon
            Polygon polygon = (Polygon) wktReader.read(wkt);

            // Parse metadata (simple JSON parsing)
            String name = extractJsonField(metadata, "name");
            String severity = extractJsonField(metadata, "severity");

            return CachedZoneRecord.fromEntity(zoneId, name, polygon, severity, wkt);

        } catch (Exception e) {
            log.error("Failed to parse cached zone: {}", zoneId, e);
            return null;
        }
    }

    /**
     * Invalidates a specific zone in the cache.
     * Call this when a zone is updated in the database.
     */
    public void invalidateZone(Long zoneId) {
        String geometryKey = ZONE_KEY_PREFIX + zoneId;
        String metadataKey = ZONE_METADATA_PREFIX + zoneId;

        stringRedisTemplate.delete(geometryKey);
        stringRedisTemplate.delete(metadataKey);
        stringRedisTemplate.opsForSet().remove(ALL_ZONES_KEY, String.valueOf(zoneId));

        log.info("Invalidated cached zone: {}", zoneId);
    }

    /**
     * Clears the entire zone cache.
     * Use with caution - system will fall back to database queries until cache is rebuilt.
     */
    public void clearCache() {
        Set<String> zoneIds = stringRedisTemplate.opsForSet().members(ALL_ZONES_KEY);

        if (zoneIds != null) {
            for (String zoneId : zoneIds) {
                invalidateZone(Long.parseLong(zoneId));
            }
        }

        stringRedisTemplate.delete(ALL_ZONES_KEY);
        log.info("Cleared all cached zones");
    }

    /**
     * Gets cache statistics for monitoring.
     */
    public CacheStats getCacheStats() {
        Set<String> zoneIds = stringRedisTemplate.opsForSet().members(ALL_ZONES_KEY);
        int cachedZoneCount = (zoneIds != null) ? zoneIds.size() : 0;
        int databaseZoneCount = (int) zoneRepository.countByActiveTrue();

        return new CacheStats(cachedZoneCount, databaseZoneCount);
    }

    /**
     * Simple JSON field extractor (avoids Jackson dependency for this simple case).
     */
    private String extractJsonField(String json, String field) {
        if (json == null) return null;

        String pattern = "\"" + field + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) return null;

        start += pattern.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return null;

        return json.substring(start, end);
    }

    /**
     * Cache statistics record for monitoring.
     */
    public record CacheStats(int cachedZoneCount, int databaseZoneCount) {
        public boolean isCacheHealthy() {
            return cachedZoneCount > 0 && cachedZoneCount == databaseZoneCount;
        }

        public double cacheHitRate() {
            if (databaseZoneCount == 0) return 0.0;
            return (double) cachedZoneCount / databaseZoneCount * 100.0;
        }
    }
}
