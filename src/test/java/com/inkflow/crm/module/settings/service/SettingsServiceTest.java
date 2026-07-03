package com.inkflow.crm.module.settings.service;

import com.inkflow.crm.common.exception.AccessDeniedException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Tenant;
import com.inkflow.crm.domain.enums.AccountType;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.audit.support.AuditLabelFormatter;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private AuditRecorder auditRecorder;

    @Mock
    private AuditLabelFormatter auditLabelFormatter;

    @InjectMocks
    private SettingsService settingsService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCompanySettings_returnsTenantProfile() {
        UUID tenantId = UUID.randomUUID();
        authenticateOwner(tenantId);

        Tenant tenant = Tenant.builder()
                .id(tenantId)
                .name("Ink Studio")
                .logoUrl("https://cdn.example.com/logo.png")
                .accountType(AccountType.STUDIO)
                .build();

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        CompanySettingsDto dto = settingsService.getCompanySettings();

        assertEquals("STUDIO", dto.getAccountType());
        assertEquals("Ink Studio", dto.getName());
        assertEquals("https://cdn.example.com/logo.png", dto.getLogoUrl());
    }

    @Test
    void updateCompanySettings_updatesLogoUrl() {
        UUID tenantId = UUID.randomUUID();
        authenticateOwner(tenantId);

        Tenant tenant = Tenant.builder()
                .id(tenantId)
                .name("Ink Studio")
                .accountType(AccountType.STUDIO)
                .build();

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(tenant)).thenReturn(tenant);

        UpdateCompanySettingsRequest request = new UpdateCompanySettingsRequest();
        request.setLogoUrl("https://cdn.example.com/new-logo.png");

        CompanySettingsDto dto = settingsService.updateCompanySettings(request);

        assertEquals("https://cdn.example.com/new-logo.png", dto.getLogoUrl());
        assertEquals("https://cdn.example.com/new-logo.png", tenant.getLogoUrl());
    }

    @Test
    void updateCompanySettings_updatesAccountTypeForOwner() {
        UUID tenantId = UUID.randomUUID();
        authenticateOwner(tenantId);

        Tenant tenant = Tenant.builder()
                .id(tenantId)
                .name("Ink Studio")
                .accountType(AccountType.SOLO)
                .build();

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(tenant)).thenReturn(tenant);

        UpdateCompanySettingsRequest request = new UpdateCompanySettingsRequest();
        request.setAccountType("STUDIO");

        CompanySettingsDto dto = settingsService.updateCompanySettings(request);

        assertEquals("STUDIO", dto.getAccountType());
        assertEquals(AccountType.STUDIO, tenant.getAccountType());
    }

    @Test
    void updateCompanySettings_rejectsAccountTypeChangeForNonOwner() {
        UUID tenantId = UUID.randomUUID();
        authenticateAdmin(tenantId);

        Tenant tenant = Tenant.builder()
                .id(tenantId)
                .name("Ink Studio")
                .accountType(AccountType.SOLO)
                .build();

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        UpdateCompanySettingsRequest request = new UpdateCompanySettingsRequest();
        request.setAccountType("STUDIO");

        assertThrows(AccessDeniedException.class, () -> settingsService.updateCompanySettings(request));
    }

    @Test
    void getCompanySettings_rejectsMissingTenant() {
        UUID tenantId = UUID.randomUUID();
        authenticateOwner(tenantId);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> settingsService.getCompanySettings());
    }

    private void authenticateOwner(UUID tenantId) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .role(UserRole.OWNER)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    private void authenticateAdmin(UUID tenantId) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .role(UserRole.ADMIN)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
