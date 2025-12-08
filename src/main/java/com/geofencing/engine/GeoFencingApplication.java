package com.geofencing.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the Real-Time Geo-Fencing Engine.
 *
 * Annotations Explained:
 * - @SpringBootApplication: Combines @Configuration, @EnableAutoConfiguration, @ComponentScan
 * - @EnableCaching: Activates Spring's annotation-driven cache management (Redis)
 * - @EnableAsync: Enables @Async annotation for asynchronous method execution
 * - @EnableScheduling: Enables @Scheduled annotation for periodic tasks (zone cache refresh)
 *
 * Architecture Overview:
 * This application processes GPS streams in real-time to detect geo-fence violations.
 *
 * Flow:
 * 1. GPS events arrive via WebSocket
 * 2. Events are validated and queued
 * 3. Background threads process events against cached zone geometries
 * 4. Violations are detected using JTS point-in-polygon algorithm
 * 5. Violations are published via WebSocket and persisted to DB
 *
 * Performance Strategy:
 * - Zone geometries are cached in Redis to avoid DB queries
 * - Async processing prevents WebSocket thread blocking
 * - Scheduled tasks refresh the cache periodically
 */
@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableScheduling
public class GeoFencingApplication {

    public static void main(String[] args) {
        SpringApplication.run(GeoFencingApplication.class, args);
    }
}
