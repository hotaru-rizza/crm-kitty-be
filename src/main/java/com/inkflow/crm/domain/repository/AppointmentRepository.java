package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID>, JpaSpecificationExecutor<Appointment> {

    @EntityGraph(attributePaths = {"client", "artist", "service", "location"})
    Page<Appointment> findByDeletedAtIsNull(Pageable pageable);

    @EntityGraph(attributePaths = {"client", "artist", "service", "location"})
    Page<Appointment> findByLocationIdAndDeletedAtIsNull(UUID locationId, Pageable pageable);

    @EntityGraph(attributePaths = {"client", "artist", "service", "location"})
    Page<Appointment> findByClientIdAndDeletedAtIsNullOrderByStartTimeDesc(UUID clientId, Pageable pageable);

    @EntityGraph(attributePaths = {"client", "artist", "service", "location"})
    Optional<Appointment> findByIdAndDeletedAtIsNull(UUID id);

    List<Appointment> findByProjectIdAndDeletedAtIsNull(UUID projectId);

    List<Appointment> findByArtistIdAndStatusInAndStartTimeAfterAndDeletedAtIsNull(UUID artistId, List<AppointmentStatus> statuses, Instant after);

    long countByLocationIdAndStartTimeBetweenAndDeletedAtIsNull(UUID locationId, Instant from, Instant to);

    long countByClientIdAndStatusAndDeletedAtIsNull(UUID clientId, AppointmentStatus status);

    @Query("""
            SELECT COALESCE(SUM(a.finalPrice), 0)
            FROM Appointment a
            WHERE a.client.id = :clientId
              AND a.deletedAt IS NULL
              AND a.status = com.inkflow.crm.domain.enums.AppointmentStatus.COMPLETED
              AND a.finalPrice > 0
            """)
    java.math.BigDecimal sumCompletedRevenueByClientId(@Param("clientId") UUID clientId);

    @Query("""
            SELECT MAX(a.startTime)
            FROM Appointment a
            WHERE a.client.id = :clientId
              AND a.deletedAt IS NULL
              AND a.status = com.inkflow.crm.domain.enums.AppointmentStatus.COMPLETED
            """)
    Optional<Instant> findLastCompletedStartTimeByClientId(@Param("clientId") UUID clientId);

    @Override
    @EntityGraph(attributePaths = {"client", "artist", "service", "location"})
    Page<Appointment> findAll(Specification<Appointment> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"client", "artist", "service", "location"})
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.startTime >= :from
              AND a.startTime < :to
              AND a.deletedAt IS NULL
              AND (:locationId IS NULL OR a.location.id = :locationId)
            ORDER BY a.startTime
            """)
    List<Appointment> findByDateRange(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("locationId") UUID locationId);

    @EntityGraph(attributePaths = {"client", "artist", "service", "location"})
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.artist.id = :artistId
              AND a.startTime >= :from
              AND a.startTime < :to
              AND a.deletedAt IS NULL
            ORDER BY a.startTime
            """)
    List<Appointment> findByArtistIdAndDateRange(
            @Param("artistId") UUID artistId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @EntityGraph(attributePaths = {"client", "artist", "service", "location"})
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.location.id = :locationId
              AND a.startTime >= :from
              AND a.startTime < :to
              AND a.deletedAt IS NULL
            ORDER BY a.startTime
            """)
    List<Appointment> findByLocationIdAndDateRange(
            @Param("locationId") UUID locationId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
            SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END
            FROM Appointment a
            WHERE a.artist.id = :artistId
              AND a.status NOT IN (
                  com.inkflow.crm.domain.enums.AppointmentStatus.CANCELLED,
                  com.inkflow.crm.domain.enums.AppointmentStatus.COMPLETED,
                  com.inkflow.crm.domain.enums.AppointmentStatus.NO_SHOW)
              AND a.deletedAt IS NULL
              AND (   (a.startTime <= :startTime AND a.endTime > :startTime)
                   OR (a.startTime < :endTime   AND a.endTime >= :endTime)
                   OR (a.startTime >= :startTime AND a.endTime <= :endTime))
            """)
    boolean existsConflictingAppointment(
            @Param("artistId") UUID artistId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime);

    @Query("""
            SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END
            FROM Appointment a
            WHERE a.artist.id = :artistId
              AND a.id != :excludeId
              AND a.status NOT IN (
                  com.inkflow.crm.domain.enums.AppointmentStatus.CANCELLED,
                  com.inkflow.crm.domain.enums.AppointmentStatus.COMPLETED,
                  com.inkflow.crm.domain.enums.AppointmentStatus.NO_SHOW)
              AND a.deletedAt IS NULL
              AND (   (a.startTime <= :startTime AND a.endTime > :startTime)
                   OR (a.startTime < :endTime   AND a.endTime >= :endTime)
                   OR (a.startTime >= :startTime AND a.endTime <= :endTime))
            """)
    boolean existsConflictingAppointmentExcluding(
            @Param("artistId") UUID artistId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            @Param("excludeId") UUID excludeId);

    @Query("""
            SELECT a FROM Appointment a
            WHERE a.artist.id = :artistId
              AND a.startTime >= :from
              AND a.startTime < :to
              AND a.status != :excludeStatus
              AND a.deletedAt IS NULL
            """)
    List<Appointment> findByArtistIdAndStartTimeBetweenAndStatusNotAndDeletedAtIsNull(
            @Param("artistId") UUID artistId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("excludeStatus") AppointmentStatus excludeStatus);

    @EntityGraph(attributePaths = {"client", "artist"})
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.startTime >= :from
              AND a.startTime < :to
              AND a.status = com.inkflow.crm.domain.enums.AppointmentStatus.SCHEDULED
              AND a.deletedAt IS NULL
            """)
    List<Appointment> findUpcomingForReminders(
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
            SELECT a.client.id FROM Appointment a
            WHERE a.deletedAt IS NULL
              AND a.client.deletedAt IS NULL
              AND a.client.blacklisted = false
              AND (:artistId IS NULL OR a.artist.id = :artistId)
            GROUP BY a.client.id
            ORDER BY MAX(a.startTime) DESC
            """)
    List<UUID> findRecentClientIds(
            @Param("artistId") UUID artistId,
            Pageable pageable);
}
