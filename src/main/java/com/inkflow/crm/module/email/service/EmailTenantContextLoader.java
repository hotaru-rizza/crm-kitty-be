package com.inkflow.crm.module.email.service;

import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.entity.CompanySettings;
import com.inkflow.crm.domain.entity.Tenant;
import com.inkflow.crm.domain.repository.CompanySettingsRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.module.email.dto.EmailTenantContext;
import com.inkflow.crm.module.email.mapper.EmailTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class EmailTenantContextLoader {

    private static final String FALLBACK_STUDIO = "INKAT";

    private final TenantRepository tenantRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final EmailTemplateMapper emailTemplateMapper;
    private final InkflowProperties inkflowProperties;

    public EmailTenantContext loadContext(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);

        return new EmailTenantContext(
                tenant != null ? tenant.getName() : FALLBACK_STUDIO,
                tenant != null ? tenant.getTimezone() : inkflowProperties.getDefaultTimezone()
        );
    }

    public Map<String, String> loadTemplateEntry(UUID tenantId, String type) {
        return companySettingsRepository.findByTenantId(tenantId)
                .map(CompanySettings::getEmailTemplates)
                .map(templates -> emailTemplateMapper.getTemplateEntry(templates, type))
                .orElse(null);
    }
}
