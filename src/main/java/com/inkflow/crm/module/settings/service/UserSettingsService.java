package com.inkflow.crm.module.settings.service;

import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.enums.AuditAction;
import com.inkflow.crm.domain.enums.AuditEntityType;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.audit.support.AuditLabelFormatter;
import com.inkflow.crm.module.settings.dto.UpdateUserSettingsRequest;
import com.inkflow.crm.module.settings.dto.UserSettingsDto;
import com.inkflow.crm.module.staff.service.StaffLookup;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSettingsService {

    private final StaffLookup staffLookup;
    private final StaffRepository staffRepository;
    private final InkflowProperties inkflowProperties;
    private final AuditRecorder auditRecorder;
    private final AuditLabelFormatter auditLabelFormatter;

    @Transactional(readOnly = true)
    public UserSettingsDto getCurrentUserSettings() {
        Staff staff = staffLookup.requireStaff(SecurityUtils.getCurrentUserId());
        return toDto(staff);
    }

    @Transactional
    public UserSettingsDto updateCurrentUserSettings(UpdateUserSettingsRequest request) {
        UUID staffId = SecurityUtils.getCurrentUserId();
        Staff staff = staffLookup.requireStaff(staffId);

        if (request.getLanguage() != null) {
            staff.setUiLanguage(request.getLanguage());
        }
        if (request.getStartPage() != null) {
            staff.setStartPage(request.getStartPage());
        }
        if (request.getAccentTheme() != null) {
            staff.setAccentTheme(request.getAccentTheme());
        }
        if (request.getColorScheme() != null) {
            staff.setColorScheme(request.getColorScheme());
        }

        staff = staffRepository.save(staff);
        log.info("User settings updated: staffId={}", staffId);
        auditRecorder.record(
                AuditAction.UPDATE,
                AuditEntityType.STAFF,
                staffId.toString(),
                auditLabelFormatter.staff(staff),
                null,
                "Особисті налаштування"
        );

        return toDto(staff);
    }

    private UserSettingsDto toDto(Staff staff) {
        String language = staff.getUiLanguage() != null
                ? staff.getUiLanguage()
                : inkflowProperties.getDefaultLanguage();
        String startPage = staff.getStartPage() != null
                ? staff.getStartPage()
                : inkflowProperties.getDefaultStartPage();
        String accentTheme = staff.getAccentTheme() != null
                ? staff.getAccentTheme()
                : inkflowProperties.getDefaultAccentTheme();
        String colorScheme = staff.getColorScheme() != null
                ? staff.getColorScheme()
                : inkflowProperties.getDefaultColorScheme();
        return new UserSettingsDto(language, startPage, accentTheme, colorScheme);
    }
}
