package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.GiftCertificate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GiftCertificateRepository extends JpaRepository<GiftCertificate, UUID> {

    @Query("SELECT g FROM GiftCertificate g WHERE g.tenantId = :tenantId AND g.deletedAt IS NULL " +
           "AND (:search IS NULL OR :search = '' OR " +
           "  LOWER(g.code) LIKE LOWER(CONCAT('%',:search,'%')) OR " +
           "  LOWER(COALESCE(g.buyerName,'')) LIKE LOWER(CONCAT('%',:search,'%')) OR " +
           "  LOWER(COALESCE(g.holderName,'')) LIKE LOWER(CONCAT('%',:search,'%'))) " +
           "ORDER BY g.createdAt DESC")
    Page<GiftCertificate> findAll(@Param("tenantId") UUID tenantId,
                                  @Param("search") String search,
                                  Pageable pageable);

    Optional<GiftCertificate> findByCodeAndTenantIdAndDeletedAtIsNull(String code, UUID tenantId);
    Optional<GiftCertificate> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);
    boolean existsByCodeAndDeletedAtIsNull(String code);
}
