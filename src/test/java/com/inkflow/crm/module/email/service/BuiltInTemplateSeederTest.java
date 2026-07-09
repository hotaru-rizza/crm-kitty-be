package com.inkflow.crm.module.email.service;

import com.inkflow.crm.domain.entity.EmailTemplate;
import com.inkflow.crm.domain.repository.EmailTemplateRepository;
import com.inkflow.crm.module.email.template.BuiltInTemplateCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuiltInTemplateSeederTest {

    private static final int BUILTIN_TEMPLATE_COUNT = BuiltInTemplateCatalog.allKeys().length;

    @Mock
    private EmailTemplateRepository emailTemplateRepository;

    @InjectMocks
    private BuiltInTemplateSeeder builtInTemplateSeeder;

    @Test
    void seedDefaultsForTenant_shouldSeedWhenBuiltinMissingForTenantEvenIfOtherTenantHasIt() {
        UUID tenantB = UUID.randomUUID();

        when(emailTemplateRepository.existsByTenantIdAndBuiltinKey(eq(tenantB), any()))
                .thenReturn(false);
        when(emailTemplateRepository.save(any(EmailTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        builtInTemplateSeeder.seedDefaultsForTenant(tenantB);

        verify(emailTemplateRepository, times(BUILTIN_TEMPLATE_COUNT))
                .existsByTenantIdAndBuiltinKey(eq(tenantB), any());
        verify(emailTemplateRepository, times(BUILTIN_TEMPLATE_COUNT)).save(any(EmailTemplate.class));
    }

    @Test
    void seedDefaultsForTenant_shouldSkipBuiltinAlreadyPresentForSameTenant() {
        UUID tenantId = UUID.randomUUID();

        when(emailTemplateRepository.existsByTenantIdAndBuiltinKey(eq(tenantId), any()))
                .thenReturn(true);

        builtInTemplateSeeder.seedDefaultsForTenant(tenantId);

        verify(emailTemplateRepository, never()).save(any(EmailTemplate.class));
    }
}
