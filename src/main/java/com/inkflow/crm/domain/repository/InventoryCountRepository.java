package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.InventoryCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryCountRepository extends JpaRepository<InventoryCount, UUID> {
    Page<InventoryCount> findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
    Optional<InventoryCount> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);
}
