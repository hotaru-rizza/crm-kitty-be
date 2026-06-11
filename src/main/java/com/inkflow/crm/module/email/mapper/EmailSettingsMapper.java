package com.inkflow.crm.module.email.mapper;

import com.inkflow.crm.domain.entity.CompanySettings;
import com.inkflow.crm.module.email.dto.EmailSettingsDto;
import org.springframework.stereotype.Component;

@Component
public class EmailSettingsMapper {

    public EmailSettingsDto toDto(CompanySettings settings) {
        return EmailSettingsDto.builder()
                .emailReminders(settings.getEmailReminders())
                .emailConfirmations(settings.getEmailConfirmations())
                .emailAftercare(settings.getEmailAftercare())
                .emailCancellation(Boolean.TRUE.equals(settings.getEmailCancellation()))
                .emailReschedule(Boolean.TRUE.equals(settings.getEmailReschedule()))
                .emailStaffNewAppointment(Boolean.TRUE.equals(settings.getEmailStaffNewAppointment()))
                .emailStaffCancellation(Boolean.TRUE.equals(settings.getEmailStaffCancellation()))
                .emailStaffReschedule(Boolean.TRUE.equals(settings.getEmailStaffReschedule()))
                .reminderHoursBefore(settings.getReminderHoursBefore())
                .build();
    }

    public EmailSettingsDto defaultDto() {
        return EmailSettingsDto.builder()
                .emailReminders(true)
                .emailConfirmations(true)
                .emailAftercare(false)
                .reminderHoursBefore(24)
                .build();
    }

    public void applyUpdate(CompanySettings settings, EmailSettingsDto dto) {
        settings.setEmailReminders(dto.isEmailReminders());
        settings.setEmailConfirmations(dto.isEmailConfirmations());
        settings.setEmailAftercare(dto.isEmailAftercare());
        settings.setEmailCancellation(dto.isEmailCancellation());
        settings.setEmailReschedule(dto.isEmailReschedule());
        settings.setEmailStaffNewAppointment(dto.isEmailStaffNewAppointment());
        settings.setEmailStaffCancellation(dto.isEmailStaffCancellation());
        settings.setEmailStaffReschedule(dto.isEmailStaffReschedule());
        if (dto.getReminderHoursBefore() > 0) {
            settings.setReminderHoursBefore(dto.getReminderHoursBefore());
        }
    }
}
