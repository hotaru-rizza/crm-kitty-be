package com.inkflow.crm.module.email.template;

import com.inkflow.crm.domain.repository.EmailTemplateOverrideRepository;
import com.inkflow.crm.module.email.enums.TemplateKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TemplateResolver {

    private final EmailTemplateOverrideRepository overrideRepository;

    public RenderedContent resolve(UUID tenantId, TemplateKey key, String locale) {
        String effectiveLocale = resolveLocale(locale);

        RenderedContent override = findOverride(tenantId, key, effectiveLocale);
        if (override != null) {
            return override;
        }

        if (!TemplateDefaults.DEFAULT_LOCALE.equals(effectiveLocale)) {
            override = findOverride(tenantId, key, TemplateDefaults.DEFAULT_LOCALE);
            if (override != null) {
                return override;
            }
        }

        return TemplateDefaults.get(key, effectiveLocale);
    }

    private RenderedContent findOverride(UUID tenantId, TemplateKey key, String locale) {
        return overrideRepository
                .findByTenantIdAndTemplateKeyAndLocale(tenantId, key.name(), locale)
                .map(entity -> new RenderedContent(entity.getSubject(), entity.getBody()))
                .orElse(null);
    }

    private String resolveLocale(String locale) {
        return locale != null && !locale.isBlank() ? locale : TemplateDefaults.DEFAULT_LOCALE;
    }
}
