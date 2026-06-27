package com.inkflow.crm.module.appointment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentNotificationDto {
    private String triggerType;
    private String triggerLabel;
    private String status;
    private Instant sentAt;
    private Instant createdAt;
}
