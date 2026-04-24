package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {
    List<Warehouse> findByTenantIdAndDeletedAtIsNullOrderByNameAsc(UUID tenantId);
    Optional<Warehouse> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);
}
