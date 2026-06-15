package com.inkflow.crm.module.email.service;

import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.enums.SupportedLocale;
import com.inkflow.crm.domain.entity.Tenant;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.module.email.dto.EmailTenantContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailTenantContextLoaderTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private InkflowProperties inkflowProperties;

    @InjectMocks
    private EmailTenantContextLoader loader;

    @Test
    void loadContext_usesFallbackWhenTenantMissing() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());
        when(inkflowProperties.getDefaultTimezone()).thenReturn("Europe/Kyiv");
        when(inkflowProperties.getDefaultLanguage()).thenReturn("uk");

        EmailTenantContext context = loader.loadContext(tenantId);

        assertThat(context.studioName()).isEqualTo("Studio");
        assertThat(context.timezone()).isEqualTo("Europe/Kyiv");
        assertThat(context.locale()).isEqualTo(SupportedLocale.UK);
    }

    @Test
    void loadContext_usesTenantDataWhenPresent() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder()
                .id(tenantId)
                .name("Ink Studio")
                .timezone("America/New_York")
                .language(SupportedLocale.EN)
                .build();

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        EmailTenantContext context = loader.loadContext(tenantId);

        assertThat(context.studioName()).isEqualTo("Ink Studio");
        assertThat(context.timezone()).isEqualTo("America/New_York");
        assertThat(context.locale()).isEqualTo(SupportedLocale.EN);
    }
}
