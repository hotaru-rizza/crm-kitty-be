package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.Location;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LocationRepository extends JpaRepository<Location, UUID> {
    Page<Location> findByTenantIdAndDeletedAtIsNull(UUID tenantId, Pageable pageable);
    List<Location> findByTenantIdAndDeletedAtIsNull(UUID tenantId);
    Optional<Location> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);
    List<Location> findByTenantIdAndIsActiveAndDeletedAtIsNull(UUID tenantId, Boolean isActive);

    Optional<Location> findFirstByTenantIdAndIsActiveTrueAndDeletedAtIsNull(UUID tenantId);
}
