package com.inkflow.crm.module.email.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailSettingsDto {
    private boolean emailReminders;
    private boolean emailConfirmations;
    private boolean emailAftercare;
    private boolean emailCancellation;
    private boolean emailReschedule;
    private boolean emailStaffNewAppointment;
    private boolean emailStaffCancellation;
    private boolean emailStaffReschedule;
    private int reminderHoursBefore;
}
