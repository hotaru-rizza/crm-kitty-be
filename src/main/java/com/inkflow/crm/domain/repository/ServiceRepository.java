package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ServiceRepository extends JpaRepository<Service, UUID> {

    Page<Service> findByTenantIdAndDeletedAtIsNull(UUID tenantId, Pageable pageable);

    List<Service> findByTenantIdAndDeletedAtIsNull(UUID tenantId);

    Optional<Service> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    List<Service> findByTenantIdAndIsActiveAndDeletedAtIsNull(UUID tenantId, Boolean isActive);

    List<Service> findByTenantIdAndIsActiveTrueAndDeletedAtIsNull(UUID tenantId);

    long countByTenantIdAndIsActiveTrueAndDeletedAtIsNull(UUID tenantId);
}
