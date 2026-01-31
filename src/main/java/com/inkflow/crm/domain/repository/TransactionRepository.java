package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.Transaction;
import com.inkflow.crm.domain.enums.TransactionCategory;
import com.inkflow.crm.domain.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    Page<Transaction> findByTenantIdAndDeletedAtIsNull(UUID tenantId, Pageable pageable);
    Optional<Transaction> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);
    Page<Transaction> findByTenantIdAndTypeAndDeletedAtIsNull(UUID tenantId, TransactionType type, Pageable pageable);
    Page<Transaction> findByTenantIdAndCategoryAndDeletedAtIsNull(UUID tenantId, TransactionCategory category, Pageable pageable);
    Page<Transaction> findByTenantIdAndStaffIdAndDeletedAtIsNull(UUID tenantId, UUID staffId, Pageable pageable);

    @Query("SELECT SUM(t.amount) FROM Transaction t WHERE t.tenantId = :tenantId AND t.type = :type AND t.date >= :from AND t.date < :to AND t.deletedAt IS NULL")
    BigDecimal sumByTypeAndDateRange(@Param("tenantId") UUID tenantId, @Param("type") TransactionType type, @Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT t.category, SUM(t.amount) FROM Transaction t WHERE t.tenantId = :tenantId AND t.date >= :from AND t.date < :to AND t.deletedAt IS NULL GROUP BY t.category")
    List<Object[]> sumByCategoryAndDateRange(@Param("tenantId") UUID tenantId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT t.paymentMethod, SUM(t.amount) FROM Transaction t WHERE t.tenantId = :tenantId AND t.type = 'INCOME' AND t.date >= :from AND t.date < :to AND t.deletedAt IS NULL GROUP BY t.paymentMethod")
    List<Object[]> sumByPaymentMethodAndDateRange(@Param("tenantId") UUID tenantId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT t.staff.id, t.staff.firstName, t.staff.lastName, SUM(t.amount), COUNT(t) FROM Transaction t WHERE t.tenantId = :tenantId AND t.type = 'INCOME' AND t.category = 'SERVICE' AND t.date >= :from AND t.date < :to AND t.staff IS NOT NULL AND t.deletedAt IS NULL GROUP BY t.staff.id, t.staff.firstName, t.staff.lastName")
    List<Object[]> sumByArtistAndDateRange(@Param("tenantId") UUID tenantId, @Param("from") Instant from, @Param("to") Instant to);

    long countByTenantIdAndDateBetweenAndDeletedAtIsNull(UUID tenantId, Instant from, Instant to);
    
    @Query("SELECT SUM(t.amount) FROM Transaction t WHERE t.tenantId = :tenantId AND t.location.id = :locationId AND t.type = 'INCOME' AND t.date >= :from AND t.date < :to AND t.deletedAt IS NULL")
    BigDecimal sumRevenueByLocationAndDateRange(@Param("tenantId") UUID tenantId, @Param("locationId") UUID locationId, @Param("from") Instant from, @Param("to") Instant to);
}
