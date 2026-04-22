package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, UUID> {
    List<Promotion> findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID tenantId);
    List<Promotion> findByTenantIdAndIsActiveTrueAndDeletedAtIsNull(UUID tenantId);
    Optional<Promotion> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);
}
