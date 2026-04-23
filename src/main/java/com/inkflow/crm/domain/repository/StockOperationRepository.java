package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.StockOperation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StockOperationRepository extends JpaRepository<StockOperation, UUID> {
    Page<StockOperation> findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
    List<StockOperation> findByProductIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID productId);
}
