package com.inkflow.crm.module.notification.event;

import java.time.Instant;
import java.util.UUID;

public record AppointmentReminderEvent(
        UUID appointmentId,
        UUID tenantId,
        UUID staffId,
        UUID clientId,
        String clientName,
        Instant startTime
) {}
