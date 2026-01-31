package com.inkflow.crm.module.settings.service;

import com.inkflow.crm.common.exception.ErrorCode;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.CompanySettings;
import com.inkflow.crm.domain.entity.Tenant;
import com.inkflow.crm.domain.repository.CompanySettingsRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.module.settings.dto.CompanySettingsDto;
import com.inkflow.crm.module.settings.dto.UpdateCompanySettingsRequest;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final CompanySettingsRepository companySettingsRepository;
    private final TenantRepository tenantRepository;

    @Transactional(readOnly = true)
    public CompanySettingsDto getCompanySettings() {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        CompanySettings settings = companySettingsRepository.findByTenantId(tenantId)
                .orElseGet(() -> createDefaultSettings(tenantId));
        return mapToDto(settings);
    }

    @Transactional
    public CompanySettingsDto updateCompanySettings(UpdateCompanySettingsRequest request) {
        SecurityUtils.requireOwner();
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        CompanySettings settings = companySettingsRepository.findByTenantId(tenantId)
                .orElseGet(() -> createDefaultSettings(tenantId));

        if (request.getSmsReminders() != null) settings.setSmsReminders(request.getSmsReminders());
        if (request.getTelegramReminders() != null) settings.setTelegramReminders(request.getTelegramReminders());
        if (request.getEmailReminders() != null) settings.setEmailReminders(request.getEmailReminders());
        if (request.getReminderHoursBefore() != null) settings.setReminderHoursBefore(request.getReminderHoursBefore());
        if (request.getWorkingHoursStart() != null) settings.setWorkingHoursStart(LocalTime.parse(request.getWorkingHoursStart()));
        if (request.getWorkingHoursEnd() != null) settings.setWorkingHoursEnd(LocalTime.parse(request.getWorkingHoursEnd()));
        if (request.getAllowOnlineBooking() != null) settings.setAllowOnlineBooking(request.getAllowOnlineBooking());
        if (request.getMinAdvanceHours() != null) settings.setMinAdvanceHours(request.getMinAdvanceHours());
        if (request.getMaxAdvanceDays() != null) settings.setMaxAdvanceDays(request.getMaxAdvanceDays());

        settings.setUpdatedBy(SecurityUtils.getCurrentUserId());

        settings = companySettingsRepository.save(settings);
        return mapToDto(settings);
    }

    private CompanySettings createDefaultSettings(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Tenant not found"));

        CompanySettings settings = CompanySettings.builder()
                .tenant(tenant)
                .smsReminders(false)
                .telegramReminders(false)
                .emailReminders(true)
                .reminderHoursBefore(24)
                .workingHoursStart(LocalTime.of(9, 0))
                .workingHoursEnd(LocalTime.of(22, 0))
                .allowOnlineBooking(true)
                .minAdvanceHours(24)
                .maxAdvanceDays(60)
                .build();

        return companySettingsRepository.save(settings);
    }

    private CompanySettingsDto mapToDto(CompanySettings settings) {
        return CompanySettingsDto.builder()
                .smsReminders(settings.getSmsReminders())
                .telegramReminders(settings.getTelegramReminders())
                .emailReminders(settings.getEmailReminders())
                .reminderHoursBefore(settings.getReminderHoursBefore())
                .workingHoursStart(settings.getWorkingHoursStart().toString())
                .workingHoursEnd(settings.getWorkingHoursEnd().toString())
                .allowOnlineBooking(settings.getAllowOnlineBooking())
                .minAdvanceHours(settings.getMinAdvanceHours())
                .maxAdvanceDays(settings.getMaxAdvanceDays())
                .updatedAt(settings.getUpdatedAt())
                .build();
    }
}
