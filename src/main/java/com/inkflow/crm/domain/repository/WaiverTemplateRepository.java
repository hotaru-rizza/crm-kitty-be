package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.WaiverTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WaiverTemplateRepository extends JpaRepository<WaiverTemplate, UUID> {
    List<WaiverTemplate> findByTenantId(UUID tenantId);
    Optional<WaiverTemplate> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<WaiverTemplate> findByTenantIdAndIsActiveTrue(UUID tenantId);
}
