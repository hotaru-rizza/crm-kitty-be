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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettingsService {

    private static final LocalTime DEFAULT_WORKDAY_START = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_WORKDAY_END = LocalTime.of(22, 0);
    private static final int DEFAULT_REMINDER_HOURS = 24;
    private static final int DEFAULT_MIN_ADVANCE_HOURS = 24;
    private static final int DEFAULT_MAX_ADVANCE_DAYS = 60;

    private final CompanySettingsRepository companySettingsRepository;
    private final TenantRepository tenantRepository;

    @Transactional(readOnly = true)
    public CompanySettingsDto getCompanySettings() {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        CompanySettings settings = findOrCreateSettings(tenantId);
        Tenant tenant = requireTenant(tenantId);

        return toDto(settings, tenant.getAccountType());
    }

    @Transactional
    public CompanySettingsDto updateCompanySettings(UpdateCompanySettingsRequest request) {
        SecurityUtils.requireOwner();

        UUID tenantId = SecurityUtils.getCurrentTenantId();
        CompanySettings settings = findOrCreateSettings(tenantId);

        applyUpdate(request, settings);
        settings.setUpdatedBy(SecurityUtils.getCurrentUserId());

        settings = companySettingsRepository.save(settings);
        Tenant tenant = requireTenant(tenantId);

        log.info("Company settings updated: tenantId={}", tenantId);
        return toDto(settings, tenant.getAccountType());
    }

    private CompanySettings findOrCreateSettings(UUID tenantId) {
        return companySettingsRepository.findByTenantId(tenantId)
                .orElseGet(() -> createDefaultSettings(tenantId));
    }

    private CompanySettings createDefaultSettings(UUID tenantId) {
        Tenant tenant = requireTenant(tenantId);

        CompanySettings settings = CompanySettings.builder()
                .tenant(tenant)
                .smsReminders(false)
                .telegramReminders(false)
                .emailReminders(true)
                .reminderHoursBefore(DEFAULT_REMINDER_HOURS)
                .workingHoursStart(DEFAULT_WORKDAY_START)
                .workingHoursEnd(DEFAULT_WORKDAY_END)
                .allowOnlineBooking(true)
                .minAdvanceHours(DEFAULT_MIN_ADVANCE_HOURS)
                .maxAdvanceDays(DEFAULT_MAX_ADVANCE_DAYS)
                .build();

        log.info("Default company settings created: tenantId={}", tenantId);
        return companySettingsRepository.save(settings);
    }

    private void applyUpdate(UpdateCompanySettingsRequest request, CompanySettings settings) {
        if (request.getSmsReminders() != null) {
            settings.setSmsReminders(request.getSmsReminders());
        }
        if (request.getTelegramReminders() != null) {
            settings.setTelegramReminders(request.getTelegramReminders());
        }
        if (request.getEmailReminders() != null) {
            settings.setEmailReminders(request.getEmailReminders());
        }
        if (request.getReminderHoursBefore() != null) {
            settings.setReminderHoursBefore(request.getReminderHoursBefore());
        }
        if (request.getWorkingHoursStart() != null) {
            settings.setWorkingHoursStart(LocalTime.parse(request.getWorkingHoursStart()));
        }
        if (request.getWorkingHoursEnd() != null) {
            settings.setWorkingHoursEnd(LocalTime.parse(request.getWorkingHoursEnd()));
        }
        if (request.getAllowOnlineBooking() != null) {
            settings.setAllowOnlineBooking(request.getAllowOnlineBooking());
        }
        if (request.getMinAdvanceHours() != null) {
            settings.setMinAdvanceHours(request.getMinAdvanceHours());
        }
        if (request.getMaxAdvanceDays() != null) {
            settings.setMaxAdvanceDays(request.getMaxAdvanceDays());
        }
    }

    private Tenant requireTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Tenant not found"));
    }

    private CompanySettingsDto toDto(CompanySettings settings, String accountType) {
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
                .accountType(accountType != null ? accountType : "STUDIO")
                .build();
    }
}
