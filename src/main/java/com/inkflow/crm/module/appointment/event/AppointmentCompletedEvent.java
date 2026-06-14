package com.inkflow.crm.module.appointment.event;

import java.util.UUID;

public record AppointmentCompletedEvent(UUID appointmentId, UUID tenantId) {}
