package com.inkflow.crm.module.appointment.event;

import java.util.UUID;

public record AppointmentRescheduledEvent(UUID appointmentId, UUID tenantId) {}
