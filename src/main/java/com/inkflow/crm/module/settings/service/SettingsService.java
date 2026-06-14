package com.inkflow.crm.module.settings.service;

import com.inkflow.crm.common.exception.ErrorCode;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Tenant;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.module.settings.dto.CompanySettingsDto;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final TenantRepository tenantRepository;

    @Transactional(readOnly = true)
    public CompanySettingsDto getCompanySettings() {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Tenant tenant = requireTenant(tenantId);

        return CompanySettingsDto.builder()
                .accountType(tenant.getAccountType().name())
                .build();
    }

    private Tenant requireTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Tenant not found"));
    }
}
