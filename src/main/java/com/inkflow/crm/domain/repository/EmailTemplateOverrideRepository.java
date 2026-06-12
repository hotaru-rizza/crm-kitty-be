package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.EmailTemplateOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailTemplateOverrideRepository extends JpaRepository<EmailTemplateOverride, UUID> {

    Optional<EmailTemplateOverride> findByTenantIdAndTemplateKeyAndLocale(
            UUID tenantId, String templateKey, String locale);

    List<EmailTemplateOverride> findAllByTenantId(UUID tenantId);

    void deleteByTenantIdAndTemplateKeyAndLocale(UUID tenantId, String templateKey, String locale);
}
