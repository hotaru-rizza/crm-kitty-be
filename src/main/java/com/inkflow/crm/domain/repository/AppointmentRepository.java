package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    Page<Appointment> findByTenantIdAndDeletedAtIsNull(UUID tenantId, Pageable pageable);
    Page<Appointment> findByTenantIdAndLocationIdAndDeletedAtIsNull(UUID tenantId, UUID locationId, Pageable pageable);
    Page<Appointment> findByTenantIdAndClientIdAndDeletedAtIsNullOrderByStartTimeDesc(UUID tenantId, UUID clientId, Pageable pageable);
    Optional<Appointment> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);
    Optional<Appointment> findByIdAndDeletedAtIsNull(UUID id);

    @Query("SELECT a FROM Appointment a WHERE a.tenantId = :tenantId AND a.startTime >= :from AND a.startTime < :to AND a.deletedAt IS NULL ORDER BY a.startTime")
    List<Appointment> findByTenantIdAndDateRange(@Param("tenantId") UUID tenantId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT a FROM Appointment a WHERE a.tenantId = :tenantId AND a.artist.id = :artistId AND a.startTime >= :from AND a.startTime < :to AND a.deletedAt IS NULL ORDER BY a.startTime")
    List<Appointment> findByTenantIdAndArtistIdAndDateRange(@Param("tenantId") UUID tenantId, @Param("artistId") UUID artistId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT a FROM Appointment a WHERE a.tenantId = :tenantId AND a.location.id = :locationId AND a.startTime >= :from AND a.startTime < :to AND a.deletedAt IS NULL ORDER BY a.startTime")
    List<Appointment> findByTenantIdAndLocationIdAndDateRange(@Param("tenantId") UUID tenantId, @Param("locationId") UUID locationId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM Appointment a WHERE a.artist.id = :artistId AND a.status NOT IN ('CANCELLED', 'DONE') AND a.deletedAt IS NULL AND ((a.startTime <= :startTime AND a.endTime > :startTime) OR (a.startTime < :endTime AND a.endTime >= :endTime) OR (a.startTime >= :startTime AND a.endTime <= :endTime))")
    boolean existsConflictingAppointment(@Param("artistId") UUID artistId, @Param("startTime") Instant startTime, @Param("endTime") Instant endTime);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM Appointment a WHERE a.artist.id = :artistId AND a.id != :excludeId AND a.status NOT IN ('CANCELLED', 'DONE') AND a.deletedAt IS NULL AND ((a.startTime <= :startTime AND a.endTime > :startTime) OR (a.startTime < :endTime AND a.endTime >= :endTime) OR (a.startTime >= :startTime AND a.endTime <= :endTime))")
    boolean existsConflictingAppointmentExcluding(@Param("artistId") UUID artistId, @Param("startTime") Instant startTime, @Param("endTime") Instant endTime, @Param("excludeId") UUID excludeId);

    @Query("SELECT a FROM Appointment a WHERE a.tenantId = :tenantId AND a.startTime >= :from AND a.startTime < :to AND a.deletedAt IS NULL AND (:artistIds IS NULL OR a.artist.id IN :artistIds) ORDER BY a.startTime")
    List<Appointment> findForCalendar(@Param("tenantId") UUID tenantId, @Param("from") Instant from, @Param("to") Instant to, @Param("artistIds") List<UUID> artistIds);


    List<Appointment> findByArtistIdAndStatusInAndStartTimeAfterAndDeletedAtIsNull(UUID artistId, List<AppointmentStatus> statuses, Instant after);
    
    long countByTenantIdAndLocationIdAndStartTimeBetweenAndDeletedAtIsNull(UUID tenantId, UUID locationId, Instant from, Instant to);

    @Query("SELECT a FROM Appointment a WHERE a.artist.id = :artistId AND a.startTime >= :from AND a.startTime < :to AND a.status != :excludeStatus AND a.deletedAt IS NULL")
    List<Appointment> findByArtistIdAndStartTimeBetweenAndStatusNotAndDeletedAtIsNull(
            @Param("artistId") UUID artistId, 
            @Param("from") Instant from, 
            @Param("to") Instant to,
            @Param("excludeStatus") AppointmentStatus excludeStatus);
}
