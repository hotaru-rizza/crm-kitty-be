package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.LeaveRequest;
import com.inkflow.crm.domain.enums.LeaveStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {

    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.tenantId = :tenantId AND lr.deletedAt IS NULL " +
           "AND lr.staff.id = :staffId ORDER BY lr.startDate DESC")
    List<LeaveRequest> findByStaffId(@Param("tenantId") UUID tenantId, @Param("staffId") UUID staffId);

    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.tenantId = :tenantId AND lr.deletedAt IS NULL " +
           "AND lr.staff.id = :staffId AND lr.status = :status ORDER BY lr.startDate DESC")
    List<LeaveRequest> findByStaffIdAndStatus(
            @Param("tenantId") UUID tenantId, 
            @Param("staffId") UUID staffId,
            @Param("status") LeaveStatus status);

    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.tenantId = :tenantId AND lr.deletedAt IS NULL " +
           "AND lr.staff.id = :staffId " +
           "AND lr.status = 'APPROVED' " +
           "AND lr.startDate <= :date AND lr.endDate >= :date")
    List<LeaveRequest> findActiveLeaveForDate(
            @Param("tenantId") UUID tenantId,
            @Param("staffId") UUID staffId,
            @Param("date") LocalDate date);

    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.tenantId = :tenantId AND lr.deletedAt IS NULL " +
           "AND lr.staff.id = :staffId " +
           "AND lr.status = 'APPROVED' " +
           "AND ((lr.startDate BETWEEN :startDate AND :endDate) OR (lr.endDate BETWEEN :startDate AND :endDate) " +
           "OR (lr.startDate <= :startDate AND lr.endDate >= :endDate))")
    List<LeaveRequest> findOverlappingLeaves(
            @Param("tenantId") UUID tenantId,
            @Param("staffId") UUID staffId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.tenantId = :tenantId AND lr.deletedAt IS NULL " +
           "AND lr.status = :status ORDER BY lr.createdAt DESC")
    Page<LeaveRequest> findByStatus(
            @Param("tenantId") UUID tenantId,
            @Param("status") LeaveStatus status,
            Pageable pageable);

    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.tenantId = :tenantId AND lr.deletedAt IS NULL " +
           "AND lr.staff.id = :staffId " +
           "AND ((lr.startDate BETWEEN :startDate AND :endDate) OR (lr.endDate BETWEEN :startDate AND :endDate))")
    List<LeaveRequest> findByStaffIdAndDateRange(
            @Param("tenantId") UUID tenantId,
            @Param("staffId") UUID staffId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
