package com.inkflow.crm.module.settings.service;

import com.inkflow.crm.common.exception.ErrorCode;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Tenant;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.module.settings.dto.ClientDormancySettingsDto;
import com.inkflow.crm.module.settings.dto.CompanySettingsDto;
import com.inkflow.crm.module.settings.dto.UpdateClientDormancySettingsRequest;
import com.inkflow.crm.module.settings.dto.UpdateCompanySettingsRequest;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsService {

    private final TenantRepository tenantRepository;

    @Transactional(readOnly = true)
    public CompanySettingsDto getCompanySettings() {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Tenant tenant = requireTenant(tenantId);

        return toDto(tenant);
    }

    @Transactional
    public CompanySettingsDto updateCompanySettings(UpdateCompanySettingsRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Tenant tenant = requireTenant(tenantId);

        if (request.getName() != null && !request.getName().isBlank()) {
            tenant.setName(request.getName().trim());
        }

        if (request.getLogoUrl() != null) {
            String logoUrl = request.getLogoUrl().isBlank() ? null : request.getLogoUrl().trim();
            tenant.setLogoUrl(logoUrl);
        }

        tenantRepository.save(tenant);
        log.info("Company settings updated via service: tenantId={}", tenantId);

        return toDto(tenant);
    }

    @Transactional(readOnly = true)
    public ClientDormancySettingsDto getClientDormancySettings() {
        Tenant tenant = requireTenant(SecurityUtils.getCurrentTenantId());
        return toClientDormancyDto(tenant);
    }

    @Transactional
    public ClientDormancySettingsDto updateClientDormancySettings(UpdateClientDormancySettingsRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Tenant tenant = requireTenant(tenantId);
        tenant.setClientDormancyDays(request.getInactivityDays());
        tenantRepository.save(tenant);
        log.info("Client dormancy settings updated: tenantId={} inactivityDays={}", tenantId, request.getInactivityDays());
        return toClientDormancyDto(tenant);
    }

    private ClientDormancySettingsDto toClientDormancyDto(Tenant tenant) {
        int inactivityDays = tenant.getClientDormancyDays() != null ? tenant.getClientDormancyDays() : 90;
        return ClientDormancySettingsDto.builder()
                .inactivityDays(inactivityDays)
                .build();
    }

    private CompanySettingsDto toDto(Tenant tenant) {
        return CompanySettingsDto.builder()
                .name(tenant.getName())
                .logoUrl(tenant.getLogoUrl())
                .accountType(tenant.getAccountType().name())
                .build();
    }

    private Tenant requireTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Tenant not found"));
    }
}
