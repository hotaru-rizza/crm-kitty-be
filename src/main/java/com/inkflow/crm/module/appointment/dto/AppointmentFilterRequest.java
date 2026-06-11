package com.inkflow.crm.module.appointment.dto;

import java.util.List;
import java.util.UUID;

public record AppointmentFilterRequest(
        UUID locationId,
        List<UUID> artistIds,
        UUID serviceId,
        String status,
        String from,
        String to
) {
}
