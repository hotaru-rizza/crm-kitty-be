package com.inkflow.crm.module.email.service;

import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.CompanySettings;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.CompanySettingsRepository;
import com.inkflow.crm.module.email.dto.EmailSettingsDto;
import com.inkflow.crm.module.email.dto.SendEmailRequest;
import com.inkflow.crm.module.email.dto.SendEmailResultDto;
import com.inkflow.crm.module.email.mapper.EmailSettingsMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailManagementServiceTest {

    @Mock private EmailService emailService;
    @Mock private ClientRepository clientRepository;
    @Mock private CompanySettingsRepository companySettingsRepository;
    @Mock private EmailSettingsMapper emailSettingsMapper;

    @InjectMocks
    private EmailManagementService emailManagementService;

    @Test
    void sendBulk_skipsClientsWithoutEmail() {
        UUID tenantId = UUID.randomUUID();
        UUID clientWithEmailId = UUID.randomUUID();
        UUID clientWithoutEmailId = UUID.randomUUID();

        Client withEmail = Client.builder()
                .id(clientWithEmailId)
                .tenantId(tenantId)
                .email("client@test.com")
                .firstName("Anna").lastName("Ink").build();
        Client withoutEmail = Client.builder()
                .id(clientWithoutEmailId)
                .tenantId(tenantId)
                .firstName("Bob").lastName("Ink").build();

        when(clientRepository.findAllById(List.of(clientWithEmailId, clientWithoutEmailId)))
                .thenReturn(List.of(withEmail, withoutEmail));

        SendEmailResultDto result = emailManagementService.sendBulk(tenantId,
                new SendEmailRequest(List.of(clientWithEmailId, clientWithoutEmailId), "Hello", "Body"));

        assertThat(result.sent()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        verify(emailService).sendManual(tenantId, "client@test.com", withEmail.getFullName(), "Hello", "Body");
    }

    @Test
    void sendBulk_skipsForeignTenantClients() {
        UUID tenantId = UUID.randomUUID();
        UUID otherTenant = UUID.randomUUID();
        UUID localId = UUID.randomUUID();
        UUID foreignId = UUID.randomUUID();

        Client local = Client.builder().id(localId).tenantId(tenantId)
                .email("local@test.com").firstName("Local").lastName("C").build();
        Client foreign = Client.builder().id(foreignId).tenantId(otherTenant)
                .email("foreign@test.com").firstName("Foreign").lastName("C").build();

        when(clientRepository.findAllById(List.of(localId, foreignId)))
                .thenReturn(List.of(local, foreign));

        SendEmailResultDto result = emailManagementService.sendBulk(tenantId,
                new SendEmailRequest(List.of(localId, foreignId), "Hi", "Body"));

        assertThat(result.sent()).isEqualTo(1);
        verify(emailService).sendManual(tenantId, "local@test.com", local.getFullName(), "Hi", "Body");
        verify(emailService, never()).sendManual(eq(tenantId), eq("foreign@test.com"), any(), any(), any());
    }

    @Test
    void sendBulk_returnsZerosForEmptyList() {
        UUID tenantId = UUID.randomUUID();
        when(clientRepository.findAllById(List.of())).thenReturn(List.of());

        SendEmailResultDto result = emailManagementService.sendBulk(tenantId,
                new SendEmailRequest(List.of(), "Hello", "Body"));

        assertThat(result.sent()).isZero();
        assertThat(result.skipped()).isZero();
        verify(emailService, never()).sendManual(any(), any(), any(), any(), any());
    }

    @Test
    void getEmailSettings_returnsMappedSettings() {
        UUID tenantId = UUID.randomUUID();
        CompanySettings settings = CompanySettings.builder().tenantId(tenantId).emailReminders(false).build();
        EmailSettingsDto dto = EmailSettingsDto.builder().emailReminders(false).build();

        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(settings));
        when(emailSettingsMapper.toDto(settings)).thenReturn(dto);

        EmailSettingsDto result = emailManagementService.getEmailSettings(tenantId);

        assertThat(result.isEmailReminders()).isFalse();
    }

    @Test
    void getEmailSettings_returnsDefaultsWhenNoSettings() {
        UUID tenantId = UUID.randomUUID();
        EmailSettingsDto defaults = EmailSettingsDto.builder().emailReminders(true).build();

        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(emailSettingsMapper.defaultDto()).thenReturn(defaults);

        EmailSettingsDto result = emailManagementService.getEmailSettings(tenantId);

        assertThat(result.isEmailReminders()).isTrue();
        verify(emailSettingsMapper).defaultDto();
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

        assertThat(result.isEmailReminders()).isFalse();
        verify(emailSettingsMapper).applyUpdate(settings, input);
    }

    @Test
    void updateEmailSettings_throwsWhenSettingsMissing() {
        UUID tenantId = UUID.randomUUID();
        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> emailManagementService.updateEmailSettings(tenantId,
                EmailSettingsDto.builder().emailReminders(true).build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
