package com.inkflow.crm.module.email.service;

import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.entity.CompanySettings;
import com.inkflow.crm.domain.entity.Tenant;
import com.inkflow.crm.domain.repository.CompanySettingsRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.module.email.dto.EmailTenantContext;
import com.inkflow.crm.module.email.mapper.EmailTemplateMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailTenantContextLoaderTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private CompanySettingsRepository companySettingsRepository;

    @Mock
    private EmailTemplateMapper emailTemplateMapper;

    @Mock
    private InkflowProperties inkflowProperties;

    @InjectMocks
    private EmailTenantContextLoader loader;

    @Test
    void shouldUseFallbackStudioWhenTenantMissing() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());
        when(inkflowProperties.getDefaultTimezone()).thenReturn("Europe/Kyiv");

        EmailTenantContext context = loader.loadContext(tenantId);

        assertEquals("INKAT", context.studioName());
        assertEquals("Europe/Kyiv", context.timezone());
    }

    @Test
    void shouldUseTenantDetailsWhenTenantExists() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder()
                .id(tenantId)
                .name("Ink Studio")
                .timezone("America/New_York")
                .build();

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        EmailTenantContext context = loader.loadContext(tenantId);

        assertEquals("Ink Studio", context.studioName());
        assertEquals("America/New_York", context.timezone());
    }

    @Test
    void shouldReturnNullTemplateEntryWhenSettingsMissing() {
        UUID tenantId = UUID.randomUUID();
        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());

        assertNull(loader.loadTemplateEntry(tenantId, "REMINDER"));
    }

    @Test
    void shouldDelegateTemplateLookupWhenSettingsExist() {
        UUID tenantId = UUID.randomUUID();
        Map<String, Map<String, String>> templates = Map.of(
                "REMINDER", Map.of("subject", "Reminder subject")
        );
        CompanySettings settings = CompanySettings.builder()
                .tenantId(tenantId)
                .emailTemplates(templates)
                .build();
        Map<String, String> entry = Map.of("subject", "Reminder subject");

        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(settings));
        when(emailTemplateMapper.getTemplateEntry(templates, "REMINDER")).thenReturn(entry);

        assertEquals(entry, loader.loadTemplateEntry(tenantId, "REMINDER"));
    }
}
