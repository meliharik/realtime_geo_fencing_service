package com.geofencing.engine.controller;

import com.geofencing.engine.dto.GpsEventRecord;
import com.geofencing.engine.dto.ZoneViolationRecord;
import com.geofencing.engine.service.GeoFencingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * WebSocket Controller for Real-Time GPS Streaming
 *
 * Message Flow:
 * 1. Scooter/Client sends GPS data to /app/gps
 * 2. Controller validates and processes GPS event
 * 3. Checks for zone violations
 * 4. Broadcasts alerts to /topic/alerts (public)
 * 5. Sends acknowledgment to sender via /user/queue/reply (private)
 *
 * Usage:
 * - Connect to: ws://localhost:8080/ws/gps-stream
 * - Send to: /app/gps
 * - Subscribe to: /topic/alerts, /topic/status
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class GpsStreamingController {

    private final GeoFencingService geoFencingService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Handle incoming GPS events from scooters
     *
     * @param gpsEvent GPS event data from scooter
     * @param principal User/scooter identity
     */
    @MessageMapping("/gps")
    public void handleGpsEvent(@Payload GpsEventRecord gpsEvent, Principal principal) {
        log.debug("Received GPS event from {}: lat={}, lon={}",
                gpsEvent.scooterId(), gpsEvent.latitude(), gpsEvent.longitude());

        try {
            // Check for zone violations
            List<ZoneViolationRecord> violations = geoFencingService.checkZoneViolation(gpsEvent);

            if (!violations.isEmpty()) {
                log.warn("Zone violation detected for scooter {}: {} violations",
                        gpsEvent.scooterId(), violations.size());

                // Broadcast alert to all subscribers of /topic/alerts
                violations.forEach(violation -> {
                    Map<String, Object> alert = Map.of(
                            "type", "VIOLATION",
                            "scooterId", gpsEvent.scooterId(),
                            "zoneName", violation.zoneName(),
                            "severity", violation.severity(),
                            "latitude", violation.latitude(),
                            "longitude", violation.longitude(),
                            "timestamp", Instant.now().toString()
                    );
                    messagingTemplate.convertAndSend("/topic/alerts", alert);
                });

                // Send private notification to the scooter
                if (principal != null) {
                    Map<String, Object> notification = Map.of(
                            "status", "VIOLATION",
                            "message", "You have entered a restricted zone!",
                            "violations", violations,
                            "timestamp", Instant.now().toString()
                    );
                    messagingTemplate.convertAndSendToUser(
                            principal.getName(),
                            "/queue/notifications",
                            notification
                    );
                }
            } else {
                log.debug("No violations for scooter {}", gpsEvent.scooterId());
            }

            // Send acknowledgment back to sender
            if (principal != null) {
                Map<String, Object> ack = Map.of(
                        "status", "OK",
                        "scooterId", gpsEvent.scooterId(),
                        "processed", true,
                        "violationsCount", violations.size(),
                        "timestamp", Instant.now().toString()
                );
                messagingTemplate.convertAndSendToUser(
                        principal.getName(),
                        "/queue/reply",
                        ack
                );
            }

        } catch (Exception e) {
            log.error("Error processing GPS event from {}: {}",
                    gpsEvent.scooterId(), e.getMessage(), e);

            // Send error notification
            if (principal != null) {
                Map<String, Object> error = Map.of(
                        "status", "ERROR",
                        "message", "Failed to process GPS event",
                        "error", e.getMessage(),
                        "timestamp", Instant.now().toString()
                );
                messagingTemplate.convertAndSendToUser(
                        principal.getName(),
                        "/queue/errors",
                        error
                );
            }
        }
    }

    /**
     * Handle GPS batch events (multiple GPS points at once)
     *
     * @param gpsEvents List of GPS events
     * @param principal User/scooter identity
     */
    @MessageMapping("/gps/batch")
    public void handleGpsBatch(@Payload List<GpsEventRecord> gpsEvents, Principal principal) {
        log.info("Received batch of {} GPS events", gpsEvents.size());

        gpsEvents.forEach(event -> handleGpsEvent(event, principal));

        // Send batch acknowledgment
        if (principal != null) {
            Map<String, Object> ack = Map.of(
                    "status", "OK",
                    "batchSize", gpsEvents.size(),
                    "processed", true,
                    "timestamp", Instant.now().toString()
            );
            messagingTemplate.convertAndSendToUser(
                    principal.getName(),
                    "/queue/reply",
                    ack
            );
        }
    }

    /**
     * Health check for WebSocket connection
     * Client sends ping, server responds with pong
     */
    @MessageMapping("/ping")
    @SendToUser("/queue/reply")
    public Map<String, Object> handlePing(Principal principal) {
        log.debug("Ping received from {}", principal != null ? principal.getName() : "anonymous");
        return Map.of(
                "type", "PONG",
                "serverTime", Instant.now().toString(),
                "status", "OK"
        );
    }

    /**
     * Subscribe to system status updates
     * Broadcasts current system statistics
     */
    @MessageMapping("/status/subscribe")
    @SendTo("/topic/status")
    public Map<String, Object> subscribeToStatus() {
        // Get current system stats
        List<com.geofencing.engine.entity.NoParkingZone> zones = geoFencingService.getAllActiveZones();

        return Map.of(
                "type", "STATUS",
                "activeZones", zones.size(),
                "timestamp", Instant.now().toString(),
                "uptime", "running"
        );
    }
}
