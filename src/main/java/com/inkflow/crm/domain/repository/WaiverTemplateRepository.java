package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.WaiverTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WaiverTemplateRepository extends JpaRepository<WaiverTemplate, UUID> {

    List<WaiverTemplate> findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID tenantId);

    Optional<WaiverTemplate> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    Optional<WaiverTemplate> findByTenantIdAndIsActiveTrueAndDeletedAtIsNull(UUID tenantId);

    // Legacy compat
    default List<WaiverTemplate> findByTenantId(UUID tenantId) {
        return findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(tenantId);
    }

    default Optional<WaiverTemplate> findByIdAndTenantId(UUID id, UUID tenantId) {
        return findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId);
    }

    default Optional<WaiverTemplate> findByTenantIdAndIsActiveTrue(UUID tenantId) {
        return findByTenantIdAndIsActiveTrueAndDeletedAtIsNull(tenantId);
    }

    @Modifying
    @Query("UPDATE WaiverTemplate t SET t.isActive = false WHERE t.tenantId = :tenantId AND t.deletedAt IS NULL")
    void deactivateAllForTenant(UUID tenantId);
}
