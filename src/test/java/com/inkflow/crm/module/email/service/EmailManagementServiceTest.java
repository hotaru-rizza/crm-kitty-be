package com.inkflow.crm.module.email.service;

import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.CompanySettings;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.CompanySettingsRepository;
import com.inkflow.crm.module.email.dto.EmailSettingsDto;
import com.inkflow.crm.module.email.dto.EmailTemplateDto;
import com.inkflow.crm.module.email.dto.SendEmailRequest;
import com.inkflow.crm.module.email.dto.SendEmailResultDto;
import com.inkflow.crm.module.email.mapper.EmailSettingsMapper;
import com.inkflow.crm.module.email.mapper.EmailTemplateMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailManagementServiceTest {

    @Mock
    private EmailService emailService;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private CompanySettingsRepository companySettingsRepository;

    @Mock
    private EmailSettingsMapper emailSettingsMapper;

    @Mock
    private EmailTemplateMapper emailTemplateMapper;

    @InjectMocks
    private EmailManagementService emailManagementService;

    @Test
    void sendBulk_skipsClientsWithoutEmail() {
        UUID tenantId = UUID.randomUUID();
        UUID clientWithEmail = UUID.randomUUID();
        UUID clientWithoutEmail = UUID.randomUUID();

        Client withEmail = Client.builder()
                .id(clientWithEmail)
                .tenantId(tenantId)
                .email("client@test.com")
                .firstName("Anna")
                .lastName("Ink")
                .build();
        Client withoutEmail = Client.builder()
                .id(clientWithoutEmail)
                .tenantId(tenantId)
                .firstName("Bob")
                .lastName("Ink")
                .build();

        when(clientRepository.findAllById(List.of(clientWithEmail, clientWithoutEmail)))
                .thenReturn(List.of(withEmail, withoutEmail));

        SendEmailRequest request = new SendEmailRequest(
                List.of(clientWithEmail, clientWithoutEmail),
                "Hello",
                "Body"
        );

        SendEmailResultDto result = emailManagementService.sendBulk(tenantId, request);

        assertEquals(1, result.sent());
        assertEquals(1, result.skipped());
        verify(emailService).sendManual(tenantId, "client@test.com", withEmail.getFullName(), "Hello", "Body");
    }

    @Test
    void getTemplates_returnsManagedTypes() {
        UUID tenantId = UUID.randomUUID();
        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(emailTemplateMapper.toDto("CONFIRMATION", null))
                .thenReturn(EmailTemplateDto.builder().type("CONFIRMATION").build());
        when(emailTemplateMapper.toDto("REMINDER", null))
                .thenReturn(EmailTemplateDto.builder().type("REMINDER").build());
        when(emailTemplateMapper.toDto("AFTERCARE", null))
                .thenReturn(EmailTemplateDto.builder().type("AFTERCARE").build());

        List<EmailTemplateDto> templates = emailManagementService.getTemplates(tenantId);

        assertEquals(3, templates.size());
    }

    @Test
    void updateEmailSettings_persistsChanges() {
        UUID tenantId = UUID.randomUUID();
        CompanySettings settings = CompanySettings.builder().tenantId(tenantId).emailReminders(true).build();
        EmailSettingsDto input = EmailSettingsDto.builder().emailReminders(false).build();
        EmailSettingsDto output = EmailSettingsDto.builder().emailReminders(false).build();

        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(settings));
        when(companySettingsRepository.save(settings)).thenReturn(settings);
        when(emailSettingsMapper.toDto(settings)).thenReturn(output);

        EmailSettingsDto result = emailManagementService.updateEmailSettings(tenantId, input);

        assertEquals(false, result.isEmailReminders());
        verify(emailSettingsMapper).applyUpdate(settings, input);
    }

    @Test
    void resetTemplate_removesCustomEntry() {
        UUID tenantId = UUID.randomUUID();
        Map<String, Map<String, String>> templates = new HashMap<>();
        templates.put("REMINDER", Map.of("subject", "Custom"));
        CompanySettings settings = CompanySettings.builder()
                .tenantId(tenantId)
                .emailTemplates(templates)
                .build();

        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(settings));

        emailManagementService.resetTemplate(tenantId, "reminder");

        verify(companySettingsRepository).save(settings);
    }

    @Test
    void updateTemplate_requiresSettings() {
        UUID tenantId = UUID.randomUUID();
        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> emailManagementService.updateTemplate(tenantId, "REMINDER", EmailTemplateDto.builder().build()));
    }

    @Test
    void shouldSkipForeignTenantClientsWhenSendBulk() {
        UUID tenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        UUID localClientId = UUID.randomUUID();
        UUID foreignClientId = UUID.randomUUID();

        Client localClient = Client.builder()
                .id(localClientId)
                .tenantId(tenantId)
                .email("local@test.com")
                .firstName("Local")
                .lastName("Client")
                .build();
        Client foreignClient = Client.builder()
                .id(foreignClientId)
                .tenantId(otherTenantId)
                .email("foreign@test.com")
                .firstName("Foreign")
                .lastName("Client")
                .build();

        when(clientRepository.findAllById(List.of(localClientId, foreignClientId)))
                .thenReturn(List.of(localClient, foreignClient));

        SendEmailResultDto result = emailManagementService.sendBulk(
                tenantId,
                new SendEmailRequest(List.of(localClientId, foreignClientId), "Hello", "Body")
        );

        assertEquals(1, result.sent());
        assertEquals(0, result.skipped());
        verify(emailService).sendManual(tenantId, "local@test.com", localClient.getFullName(), "Hello", "Body");
        verify(emailService, never()).sendManual(
                tenantId, "foreign@test.com", foreignClient.getFullName(), "Hello", "Body");
    }

    @Test
    void shouldReturnDefaultSettingsWhenTenantHasNoCompanySettings() {
        UUID tenantId = UUID.randomUUID();
        EmailSettingsDto defaults = EmailSettingsDto.builder().emailReminders(true).build();

        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(emailSettingsMapper.defaultDto()).thenReturn(defaults);

        EmailSettingsDto result = emailManagementService.getEmailSettings(tenantId);

        assertEquals(true, result.isEmailReminders());
        verify(emailSettingsMapper).defaultDto();
    }

    @Test
    void shouldDoNothingWhenResetTemplateAndTemplatesNull() {
        UUID tenantId = UUID.randomUUID();
        CompanySettings settings = CompanySettings.builder()
                .tenantId(tenantId)
                .emailTemplates(null)
                .build();

        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(settings));

        emailManagementService.resetTemplate(tenantId, "REMINDER");

        verify(companySettingsRepository, never()).save(any());
    }

    @Test
    void shouldUseCustomTemplatesWhenGetTemplates() {
        UUID tenantId = UUID.randomUUID();
        Map<String, Map<String, String>> custom = Map.of(
                "REMINDER", Map.of("subject", "Custom reminder")
        );
        CompanySettings settings = CompanySettings.builder()
                .tenantId(tenantId)
                .emailTemplates(custom)
                .build();

        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(settings));
        when(emailTemplateMapper.toDto("CONFIRMATION", custom))
                .thenReturn(EmailTemplateDto.builder().type("CONFIRMATION").build());
        when(emailTemplateMapper.toDto("REMINDER", custom))
                .thenReturn(EmailTemplateDto.builder().type("REMINDER").subject("Custom reminder").build());
        when(emailTemplateMapper.toDto("AFTERCARE", custom))
                .thenReturn(EmailTemplateDto.builder().type("AFTERCARE").build());

        List<EmailTemplateDto> templates = emailManagementService.getTemplates(tenantId);

        assertEquals(3, templates.size());
        assertEquals("Custom reminder", templates.stream()
                .filter(t -> "REMINDER".equals(t.getType()))
                .findFirst()
                .orElseThrow()
                .getSubject());
    }

    @Test
    void shouldNormalizeTemplateTypeWhenUpdateTemplate() {
        UUID tenantId = UUID.randomUUID();
        CompanySettings settings = CompanySettings.builder().tenantId(tenantId).build();
        EmailTemplateDto input = EmailTemplateDto.builder()
                .subject("Custom subject")
                .body("Custom body")
                .fields(List.of("clientName"))
                .build();
        Map<String, String> storageEntry = Map.of("subject", "Custom subject", "body", "Custom body", "fields", "clientName");

        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(settings));
        when(emailTemplateMapper.templatesOrEmpty(settings)).thenReturn(new HashMap<>());
        when(emailTemplateMapper.toStorageEntry(input)).thenReturn(storageEntry);
        when(companySettingsRepository.save(settings)).thenReturn(settings);

        EmailTemplateDto result = emailManagementService.updateTemplate(tenantId, "reminder", input);

        assertEquals("REMINDER", result.getType());
        verify(companySettingsRepository).save(settings);
    }
}
