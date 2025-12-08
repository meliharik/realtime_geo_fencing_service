package com.geofencing.engine.repository;

import com.geofencing.engine.entity.ZoneViolation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for ZoneViolation entities.
 *
 * Design for Analytics:
 * This repository supports both real-time violation storage and
 * analytical queries for dashboards and reporting.
 */
@Repository
public interface ZoneViolationRepository extends JpaRepository<ZoneViolation, Long> {

    /**
     * Finds a violation by its unique violation ID.
     */
    Optional<ZoneViolation> findByViolationId(String violationId);

    /**
     * Finds all violations for a specific scooter.
     * Ordered by most recent first.
     */
    List<ZoneViolation> findByScooterIdOrderByTimestampDesc(String scooterId);

    /**
     * Finds recent violations for a scooter (paginated).
     * Use case: "Show last 10 violations for scooter X"
     */
    List<ZoneViolation> findByScooterIdOrderByTimestampDesc(String scooterId, Pageable pageable);

    /**
     * Finds violations in a specific time range.
     * Use case: "Show all violations in the last hour"
     */
    List<ZoneViolation> findByTimestampBetweenOrderByTimestampDesc(
        Instant startTime,
        Instant endTime
    );

    /**
     * Finds violations for a specific zone.
     * Use case: "Which scooters violated zone X?"
     */
    List<ZoneViolation> findByZoneIdOrderByTimestampDesc(Long zoneId);

    /**
     * Finds high-severity violations in a time range.
     * Use case: Dashboard showing critical violations
     */
    List<ZoneViolation> findBySeverityAndTimestampAfterOrderByTimestampDesc(
        String severity,
        Instant since
    );

    /**
     * Counts violations for a scooter in a time period.
     * Use case: "How many violations did scooter X have today?"
     */
    @Query("""
        SELECT COUNT(v) FROM ZoneViolation v
        WHERE v.scooterId = :scooterId
        AND v.timestamp BETWEEN :startTime AND :endTime
        """)
    long countViolationsByScooterInTimeRange(
        @Param("scooterId") String scooterId,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime
    );

    /**
     * Gets violation statistics by zone (for analytics).
     * Returns: zone_id, zone_name, violation_count
     */
    @Query("""
        SELECT v.zoneId, v.zoneName, COUNT(v)
        FROM ZoneViolation v
        WHERE v.timestamp >= :since
        GROUP BY v.zoneId, v.zoneName
        ORDER BY COUNT(v) DESC
        """)
    List<Object[]> getViolationStatsByZone(@Param("since") Instant since);

    /**
     * Gets violation statistics by scooter (for analytics).
     * Returns: scooter_id, violation_count
     */
    @Query("""
        SELECT v.scooterId, COUNT(v)
        FROM ZoneViolation v
        WHERE v.timestamp >= :since
        GROUP BY v.scooterId
        ORDER BY COUNT(v) DESC
        """)
    List<Object[]> getViolationStatsByScooter(@Param("since") Instant since);

    /**
     * Checks if a scooter has recent violations (within last N minutes).
     * Use case: Rate limiting violation alerts
     */
    @Query("""
        SELECT COUNT(v) > 0 FROM ZoneViolation v
        WHERE v.scooterId = :scooterId
        AND v.zoneId = :zoneId
        AND v.timestamp >= :since
        """)
    boolean hasRecentViolation(
        @Param("scooterId") String scooterId,
        @Param("zoneId") Long zoneId,
        @Param("since") Instant since
    );
}
