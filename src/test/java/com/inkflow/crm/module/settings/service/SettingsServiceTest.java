package com.inkflow.crm.module.settings.service;

import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Tenant;
import com.inkflow.crm.domain.enums.AccountType;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.module.settings.dto.CompanySettingsDto;
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

    @InjectMocks
    private SettingsService settingsService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCompanySettings_returnsAccountTypeFromTenant() {
        UUID tenantId = UUID.randomUUID();
        authenticateOwner(tenantId);

        Tenant tenant = Tenant.builder()
                .id(tenantId)
                .accountType(AccountType.STUDIO)
                .build();

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        CompanySettingsDto dto = settingsService.getCompanySettings();

        assertEquals("STUDIO", dto.getAccountType());
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
                .role(com.inkflow.crm.domain.enums.UserRole.OWNER)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
