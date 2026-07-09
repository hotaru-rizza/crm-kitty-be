package com.inkflow.crm.module.email.service;

import com.inkflow.crm.config.BypassTenantFilter;
import com.inkflow.crm.domain.entity.EmailTemplate;
import com.inkflow.crm.domain.repository.EmailTemplateRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.module.email.enums.BuiltInTemplateKey;
import com.inkflow.crm.module.email.template.BuiltInTemplateCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@BypassTenantFilter
@RequiredArgsConstructor
public class BuiltInTemplateSeeder {

    private final EmailTemplateRepository emailTemplateRepository;
    private final TenantRepository tenantRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void seedDefaultsForTenant(UUID tenantId) {
        for (BuiltInTemplateKey builtinKey : BuiltInTemplateCatalog.allKeys()) {
            seedBuiltinIfMissing(tenantId, builtinKey);
        }
    }

    @Transactional
    public void seedAllTenants() {
        tenantRepository.findAll().forEach(tenant -> seedDefaultsForTenant(tenant.getId()));
        log.info("Built-in email templates seeded for all tenants");
    }

    private void seedBuiltinIfMissing(UUID tenantId, BuiltInTemplateKey builtinKey) {
        if (emailTemplateRepository.existsByTenantIdAndBuiltinKey(tenantId, builtinKey.name())) {
            return;
        }

        BuiltInTemplateCatalog.Entry catalogEntry = BuiltInTemplateCatalog.get(builtinKey);
        EmailTemplate template = EmailTemplate.builder()
                .tenantId(tenantId)
                .triggerType(builtinKey.getTriggerType())
                .offsetMinutes(builtinKey.getDefaultOffsetMinutes())
                .subject(catalogEntry.subject())
                .body(catalogEntry.body())
                .enabled(defaultEnabled(builtinKey))
                .deletable(builtinKey.isDeletable())
                .builtinKey(builtinKey.name())
                .category(builtinKey.getCategory())
                .build();

        emailTemplateRepository.save(template);
    }

    private boolean defaultEnabled(BuiltInTemplateKey builtinKey) {
        return switch (builtinKey) {
            case AFTERCARE, REVIEW_REQUEST, PREP_INSTRUCTIONS, BIRTHDAY, WINBACK -> false;
            default -> true;
        };
    }
}
