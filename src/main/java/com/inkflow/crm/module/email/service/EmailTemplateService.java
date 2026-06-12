package com.inkflow.crm.module.email.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ErrorCode;
import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.entity.EmailTemplateOverride;
import com.inkflow.crm.domain.repository.EmailTemplateOverrideRepository;
import com.inkflow.crm.module.email.dto.RenderedEmail;
import com.inkflow.crm.module.email.dto.TemplateListItemDto;
import com.inkflow.crm.module.email.dto.UpdateTemplateRequest;
import com.inkflow.crm.module.email.enums.TemplateKey;
import com.inkflow.crm.module.email.enums.TemplateVar;
import com.inkflow.crm.module.email.template.RenderedContent;
import com.inkflow.crm.module.email.template.TemplateDefaults;
import com.inkflow.crm.module.email.template.TemplateVarSubstitutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailTemplateService {

    private static final String PREVIEW_STUDIO_NAME = "Studio Name";

    private final EmailTemplateOverrideRepository overrideRepository;
    private final EmailContentRenderer contentRenderer;
    private final InkflowProperties inkflowProperties;

    @Transactional(readOnly = true)
    public List<TemplateListItemDto> listConfigurable(UUID tenantId, String locale) {
        String effectiveLocale = resolveLocale(locale);
        Map<String, EmailTemplateOverride> overridesByKey = loadOverrides(tenantId, effectiveLocale);

        return Arrays.stream(TemplateKey.values())
                .filter(TemplateKey::isConfigurable)
                .map(key -> toListItem(
                        key,
                        resolveContent(key, overridesByKey, effectiveLocale),
                        overridesByKey.containsKey(key.name())))
                .toList();
    }

    @Transactional
    public TemplateListItemDto upsertOverride(UUID tenantId, String keyName, String locale,
                                              UpdateTemplateRequest request, UUID updatedBy) {
        TemplateKey key = resolveConfigurableKey(keyName);
        String effectiveLocale = resolveLocale(locale);

        validateVars(request.body(), key);

        EmailTemplateOverride override = overrideRepository
                .findByTenantIdAndTemplateKeyAndLocale(tenantId, key.name(), effectiveLocale)
                .orElseGet(() -> EmailTemplateOverride.builder()
                        .tenantId(tenantId)
                        .templateKey(key.name())
                        .locale(effectiveLocale)
                        .build());

        override.setSubject(request.subject());
        override.setBody(request.body());
        override.setUpdatedBy(updatedBy);
        overrideRepository.save(override);

        log.info("Template override saved: tenantId={} key={} locale={}", tenantId, key, effectiveLocale);

        return toListItem(key, new RenderedContent(request.subject(), request.body()), true);
    }

    @Transactional
    public void resetOverride(UUID tenantId, String keyName, String locale) {
        TemplateKey key = resolveConfigurableKey(keyName);
        String effectiveLocale = resolveLocale(locale);

        overrideRepository.deleteByTenantIdAndTemplateKeyAndLocale(tenantId, key.name(), effectiveLocale);
        log.info("Template override reset: tenantId={} key={} locale={}", tenantId, key, effectiveLocale);
    }

    @Transactional(readOnly = true)
    public String preview(UUID tenantId, String keyName, String locale) {
        TemplateKey key = resolveConfigurableKey(keyName);
        String effectiveLocale = resolveLocale(locale);

        RenderedEmail email = contentRenderer.render(
                tenantId,
                key,
                contentRenderer.sampleVariables(key),
                effectiveLocale,
                PREVIEW_STUDIO_NAME
        );

        return email.html();
    }

    private Map<String, EmailTemplateOverride> loadOverrides(UUID tenantId, String locale) {
        return overrideRepository.findAllByTenantId(tenantId).stream()
                .filter(override -> override.getLocale().equals(locale))
                .collect(Collectors.toMap(EmailTemplateOverride::getTemplateKey, override -> override));
    }

    private RenderedContent resolveContent(TemplateKey key, Map<String, EmailTemplateOverride> overridesByKey,
                                           String locale) {
        EmailTemplateOverride override = overridesByKey.get(key.name());
        if (override != null) {
            return new RenderedContent(override.getSubject(), override.getBody());
        }

        return TemplateDefaults.get(key, locale);
    }

    private TemplateListItemDto toListItem(TemplateKey key, RenderedContent content, boolean overridden) {
        return TemplateListItemDto.builder()
                .key(key.name())
                .category(key.getCategory())
                .subject(content.subject())
                .body(content.body())
                .availableVars(key.getRequiredVars().stream()
                        .map(TemplateVar::getPlaceholder)
                        .sorted()
                        .toList())
                .isOverridden(overridden)
                .build();
    }

    private TemplateKey resolveConfigurableKey(String keyName) {
        TemplateKey key;
        try {
            key = TemplateKey.valueOf(keyName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_ERROR, "Unknown template key: " + keyName);
        }

        if (!key.isConfigurable()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_ERROR,
                    "Template " + keyName + " is not configurable by studios");
        }

        return key;
    }

    private void validateVars(String body, TemplateKey key) {
        Set<String> missing = TemplateVarSubstitutor.missingVars(body, key);
        if (!missing.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_ERROR,
                    "Body is missing required variables: " + missing);
        }
    }

    private String resolveLocale(String locale) {
        if (locale != null && !locale.isBlank()) {
            return locale;
        }

        return inkflowProperties.getDefaultLanguage();
    }
}
