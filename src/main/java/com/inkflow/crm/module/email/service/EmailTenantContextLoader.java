package com.inkflow.crm.module.email.service;

import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.entity.Tenant;
import com.inkflow.crm.domain.enums.SupportedLocale;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.module.email.dto.EmailTenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class EmailTenantContextLoader {

    private static final String FALLBACK_STUDIO = "Studio";

    private final TenantRepository tenantRepository;
    private final InkflowProperties inkflowProperties;

    public EmailTenantContext loadContext(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);

        return new EmailTenantContext(
                tenant != null ? tenant.getName() : FALLBACK_STUDIO,
                tenant != null ? tenant.getTimezone() : inkflowProperties.getDefaultTimezone(),
                tenant != null ? tenant.getLanguage() : SupportedLocale.fromCode(inkflowProperties.getDefaultLanguage())
        );
    }
}
