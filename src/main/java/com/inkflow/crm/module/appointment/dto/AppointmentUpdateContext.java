package com.inkflow.crm.module.appointment.dto;

import com.inkflow.crm.domain.enums.AppointmentStatus;

public record AppointmentUpdateContext(
        AppointmentStatus previousStatus,
        String requestedStatus,
        boolean startTimeChanged
) {
}
