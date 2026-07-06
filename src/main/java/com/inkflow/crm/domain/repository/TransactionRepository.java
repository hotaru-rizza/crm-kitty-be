package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.Transaction;
import com.inkflow.crm.domain.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    Page<Transaction> findByDeletedAtIsNull(Pageable pageable);

    Optional<Transaction> findByIdAndDeletedAtIsNull(UUID id);

    Page<Transaction> findByTypeAndDeletedAtIsNull(TransactionType type, Pageable pageable);

    Page<Transaction> findByCategoryAndDeletedAtIsNull(String category, Pageable pageable);

    Page<Transaction> findByStaffIdAndDeletedAtIsNull(UUID staffId, Pageable pageable);

    List<Transaction> findByAppointmentIdAndDeletedAtIsNullOrderByDateDesc(UUID appointmentId);

    long countByDateBetweenAndDeletedAtIsNull(Instant from, Instant to);

    @Query("""
            SELECT t FROM Transaction t
            WHERE t.staff.id = :staffId
              AND t.date >= :from
              AND t.date < :to
              AND t.deletedAt IS NULL
            """)
    Page<Transaction> findByStaffIdAndDateRangeAndDeletedAtIsNull(
            @Param("staffId") UUID staffId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    @Query("""
            SELECT t FROM Transaction t
            WHERE t.date >= :from
              AND t.date < :to
              AND t.deletedAt IS NULL
            """)
    Page<Transaction> findByDateRangeAndDeletedAtIsNull(
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    @Query("""
            SELECT t FROM Transaction t
            WHERE t.type = :type
              AND t.date >= :from
              AND t.date < :to
              AND t.deletedAt IS NULL
            """)
    Page<Transaction> findByTypeAndDateRangeAndDeletedAtIsNull(
            @Param("type") TransactionType type,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    @Query("""
            SELECT SUM(t.amount) FROM Transaction t
            WHERE t.type = :type
              AND t.date >= :from
              AND t.date < :to
              AND t.deletedAt IS NULL
              AND (:locationId IS NULL OR t.location.id = :locationId)
            """)
    BigDecimal sumByTypeAndDateRange(
            @Param("type") TransactionType type,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("locationId") UUID locationId);

    @Query("""
            SELECT SUM(t.amount) FROM Transaction t
            WHERE t.type = :type
              AND t.date >= :from
              AND t.date < :to
              AND t.staff.id IN :staffIds
              AND t.deletedAt IS NULL
              AND (:locationId IS NULL OR t.location.id = :locationId)
            """)
    BigDecimal sumByTypeAndDateRangeForStaffs(
            @Param("type") TransactionType type,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("staffIds") List<UUID> staffIds,
            @Param("locationId") UUID locationId);

    @Query("""
            SELECT COUNT(t) FROM Transaction t
            WHERE t.type = :type
              AND t.date >= :from
              AND t.date < :to
              AND t.deletedAt IS NULL
              AND (:locationId IS NULL OR t.location.id = :locationId)
            """)
    long countByTypeAndDateRange(
            @Param("type") TransactionType type,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("locationId") UUID locationId);

    @Query("""
            SELECT COUNT(t) FROM Transaction t
            WHERE t.type = :type
              AND t.date >= :from
              AND t.date < :to
              AND t.staff.id IN :staffIds
              AND t.deletedAt IS NULL
              AND (:locationId IS NULL OR t.location.id = :locationId)
            """)
    long countByTypeAndDateRangeForStaffs(
            @Param("type") TransactionType type,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("staffIds") List<UUID> staffIds,
            @Param("locationId") UUID locationId);

    @Query("""
            SELECT t.category, SUM(t.amount) FROM Transaction t
            WHERE t.date >= :from
              AND t.date < :to
              AND t.deletedAt IS NULL
              AND (:locationId IS NULL OR t.location.id = :locationId)
            GROUP BY t.category
            """)
    List<Object[]> sumByCategoryAndDateRange(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("locationId") UUID locationId);

    @Query("""
            SELECT t.category, SUM(t.amount) FROM Transaction t
            WHERE t.date >= :from
              AND t.date < :to
              AND t.staff.id IN :staffIds
              AND t.deletedAt IS NULL
              AND (:locationId IS NULL OR t.location.id = :locationId)
            GROUP BY t.category
            """)
    List<Object[]> sumByCategoryAndDateRangeForStaffs(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("staffIds") List<UUID> staffIds,
            @Param("locationId") UUID locationId);

    @Query("""
            SELECT t.paymentMethod, SUM(t.amount) FROM Transaction t
            WHERE t.type = 'INCOME'
              AND t.date >= :from
              AND t.date < :to
              AND t.deletedAt IS NULL
              AND (:locationId IS NULL OR t.location.id = :locationId)
            GROUP BY t.paymentMethod
            """)
    List<Object[]> sumByPaymentMethodAndDateRange(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("locationId") UUID locationId);

    @Query("""
            SELECT t.paymentMethod, SUM(t.amount) FROM Transaction t
            WHERE t.type = 'INCOME'
              AND t.date >= :from
              AND t.date < :to
              AND t.staff.id IN :staffIds
              AND t.deletedAt IS NULL
              AND (:locationId IS NULL OR t.location.id = :locationId)
            GROUP BY t.paymentMethod
            """)
    List<Object[]> sumByPaymentMethodAndDateRangeForStaffs(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("staffIds") List<UUID> staffIds,
            @Param("locationId") UUID locationId);

    @Query("""
            SELECT t.staff.id, t.staff.firstName, t.staff.lastName,
                   SUM(t.amount), COUNT(t), t.staff.calendarColor
            FROM Transaction t
            WHERE t.type = 'INCOME'
              AND t.category = 'service'
              AND t.date >= :from
              AND t.date < :to
              AND t.staff IS NOT NULL
              AND t.deletedAt IS NULL
              AND (:locationId IS NULL OR t.location.id = :locationId)
            GROUP BY t.staff.id, t.staff.firstName, t.staff.lastName, t.staff.calendarColor
            """)
    List<Object[]> sumByArtistAndDateRange(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("locationId") UUID locationId);

    @Query("""
            SELECT t.staff.id, t.staff.firstName, t.staff.lastName,
                   SUM(t.amount), COUNT(t), t.staff.calendarColor
            FROM Transaction t
            WHERE t.type = 'INCOME'
              AND t.category = 'service'
              AND t.date >= :from
              AND t.date < :to
              AND t.staff.id IN :staffIds
              AND t.staff IS NOT NULL
              AND t.deletedAt IS NULL
              AND (:locationId IS NULL OR t.location.id = :locationId)
            GROUP BY t.staff.id, t.staff.firstName, t.staff.lastName, t.staff.calendarColor
            """)
    List<Object[]> sumByArtistAndDateRangeForStaffs(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("staffIds") List<UUID> staffIds,
            @Param("locationId") UUID locationId);

    @Query(value = """
            SELECT t.date::date AS day, SUM(t.amount)
            FROM transactions t
            WHERE t.tenant_id = :tenantId
              AND t.type = 'INCOME'
              AND t.date >= :from
              AND t.date < :to
              AND t.deleted_at IS NULL
              AND (:locationId IS NULL OR t.location_id = :locationId)
            GROUP BY t.date::date
            ORDER BY t.date::date
            """, nativeQuery = true)
    List<Object[]> sumIncomeByDayAndDateRange(
            @Param("tenantId") UUID tenantId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("locationId") UUID locationId);

    @Query(value = """
            SELECT t.date::date AS day, SUM(t.amount)
            FROM transactions t
            WHERE t.tenant_id = :tenantId
              AND t.staff_id IN (:staffIds)
              AND t.type = 'INCOME'
              AND t.date >= :from
              AND t.date < :to
              AND t.deleted_at IS NULL
              AND (:locationId IS NULL OR t.location_id = :locationId)
            GROUP BY t.date::date
            ORDER BY t.date::date
            """, nativeQuery = true)
    List<Object[]> sumIncomeByDayAndDateRangeForStaffs(
            @Param("tenantId") UUID tenantId,
            @Param("staffIds") List<UUID> staffIds,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("locationId") UUID locationId);

    @Query("""
            SELECT SUM(t.amount) FROM Transaction t
            WHERE t.location.id = :locationId
              AND t.type = 'INCOME'
              AND t.date >= :from
              AND t.date < :to
              AND t.deletedAt IS NULL
            """)
    BigDecimal sumRevenueByLocationAndDateRange(
            @Param("locationId") UUID locationId,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
