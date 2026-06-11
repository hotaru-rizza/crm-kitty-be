package com.inkflow.crm.module.settings.service;

import com.inkflow.crm.common.exception.AccessDeniedException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.CompanySettings;
import com.inkflow.crm.domain.entity.Tenant;
import com.inkflow.crm.domain.repository.CompanySettingsRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.module.settings.dto.CompanySettingsDto;
import com.inkflow.crm.module.settings.dto.UpdateCompanySettingsRequest;
import com.inkflow.crm.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    @Mock
    private CompanySettingsRepository companySettingsRepository;

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private SettingsService settingsService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCompanySettings_returnsExistingSettings() {
        UUID tenantId = UUID.randomUUID();
        authenticateOwner(tenantId);

        CompanySettings settings = CompanySettings.builder()
                .tenantId(tenantId)
                .emailReminders(true)
                .allowOnlineBooking(true)
                .workingHoursStart(java.time.LocalTime.of(9, 0))
                .workingHoursEnd(java.time.LocalTime.of(22, 0))
                .build();
        Tenant tenant = Tenant.builder().id(tenantId).accountType("STUDIO").build();

        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(settings));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        CompanySettingsDto dto = settingsService.getCompanySettings();

        assertEquals(true, dto.getEmailReminders());
        assertEquals("STUDIO", dto.getAccountType());
    }

    @Test
    void updateCompanySettings_rejectsNonOwner() {
        UUID tenantId = UUID.randomUUID();
        authenticateArtist(tenantId);

        assertThrows(AccessDeniedException.class,
                () -> settingsService.updateCompanySettings(new UpdateCompanySettingsRequest()));
    }

    @Test
    void getCompanySettings_createsDefaultsWhenMissing() {
        UUID tenantId = UUID.randomUUID();
        authenticateOwner(tenantId);

        Tenant tenant = Tenant.builder().id(tenantId).accountType("STUDIO").build();
        CompanySettings savedDefaults = CompanySettings.builder()
                .tenantId(tenantId)
                .smsReminders(false)
                .telegramReminders(false)
                .emailReminders(true)
                .reminderHoursBefore(24)
                .workingHoursStart(java.time.LocalTime.of(9, 0))
                .workingHoursEnd(java.time.LocalTime.of(22, 0))
                .allowOnlineBooking(true)
                .minAdvanceHours(24)
                .maxAdvanceDays(60)
                .build();

        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(companySettingsRepository.save(any(CompanySettings.class))).thenReturn(savedDefaults);

        CompanySettingsDto dto = settingsService.getCompanySettings();

        assertTrue(dto.getEmailReminders());
        assertTrue(dto.getAllowOnlineBooking());
        assertEquals("09:00", dto.getWorkingHoursStart());
        assertEquals("STUDIO", dto.getAccountType());
        verify(companySettingsRepository).save(any(CompanySettings.class));
    }

    @Test
    void updateCompanySettings_persistsChangesForOwner() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        authenticateOwner(tenantId, userId);

        CompanySettings settings = CompanySettings.builder()
                .tenantId(tenantId)
                .emailReminders(true)
                .allowOnlineBooking(false)
                .workingHoursStart(java.time.LocalTime.of(9, 0))
                .workingHoursEnd(java.time.LocalTime.of(22, 0))
                .build();
        Tenant tenant = Tenant.builder().id(tenantId).accountType("SOLO").build();

        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(settings));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(companySettingsRepository.save(any(CompanySettings.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateCompanySettingsRequest request = new UpdateCompanySettingsRequest();
        request.setAllowOnlineBooking(true);

        CompanySettingsDto dto = settingsService.updateCompanySettings(request);

        assertEquals(true, dto.getAllowOnlineBooking());
        verify(companySettingsRepository).save(settings);
    }

    @Test
    void updateCompanySettings_appliesMultipleFields() {
        UUID tenantId = UUID.randomUUID();
        authenticateOwner(tenantId);

        CompanySettings settings = CompanySettings.builder()
                .tenantId(tenantId)
                .smsReminders(false)
                .telegramReminders(false)
                .emailReminders(true)
                .reminderHoursBefore(24)
                .workingHoursStart(java.time.LocalTime.of(9, 0))
                .workingHoursEnd(java.time.LocalTime.of(22, 0))
                .allowOnlineBooking(true)
                .minAdvanceHours(24)
                .maxAdvanceDays(60)
                .build();
        Tenant tenant = Tenant.builder().id(tenantId).accountType("STUDIO").build();

        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(settings));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(companySettingsRepository.save(any(CompanySettings.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateCompanySettingsRequest request = new UpdateCompanySettingsRequest();
        request.setSmsReminders(true);
        request.setReminderHoursBefore(48);
        request.setWorkingHoursStart("10:30");
        request.setMaxAdvanceDays(90);

        CompanySettingsDto dto = settingsService.updateCompanySettings(request);

        assertEquals(true, settings.getSmsReminders());
        assertEquals(48, settings.getReminderHoursBefore());
        assertEquals(java.time.LocalTime.of(10, 30), settings.getWorkingHoursStart());
        assertEquals(90, settings.getMaxAdvanceDays());
        assertEquals("10:30", dto.getWorkingHoursStart());
        verify(companySettingsRepository).save(settings);
    }

    @Test
    void getCompanySettings_rejectsMissingTenant() {
        UUID tenantId = UUID.randomUUID();
        authenticateOwner(tenantId);

        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> settingsService.getCompanySettings());
    }

    private void authenticateOwner(UUID tenantId) {
        authenticateOwner(tenantId, UUID.randomUUID());
    }

    private void authenticateOwner(UUID tenantId, UUID userId) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(userId)
                .tenantId(tenantId)
                .role(com.inkflow.crm.domain.enums.UserRole.OWNER)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    private void authenticateArtist(UUID tenantId) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .role(com.inkflow.crm.domain.enums.UserRole.ARTIST)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
