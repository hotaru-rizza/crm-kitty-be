package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    List<Product> findByTenantIdAndDeletedAtIsNullOrderByNameAsc(UUID tenantId);
    Optional<Product> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);
    List<Product> findByTenantIdAndIsActiveTrueAndDeletedAtIsNull(UUID tenantId);
}
