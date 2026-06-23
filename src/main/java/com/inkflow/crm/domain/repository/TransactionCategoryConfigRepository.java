package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.TransactionCategoryConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionCategoryConfigRepository extends JpaRepository<TransactionCategoryConfig, UUID> {

    List<TransactionCategoryConfig> findByTenantIdAndDeletedAtIsNullOrderByIsDefaultDescLabelAsc(UUID tenantId);

    Optional<TransactionCategoryConfig> findByTenantIdAndCategoryKeyAndDeletedAtIsNull(UUID tenantId, String categoryKey);

    boolean existsByTenantIdAndDeletedAtIsNull(UUID tenantId);
}
