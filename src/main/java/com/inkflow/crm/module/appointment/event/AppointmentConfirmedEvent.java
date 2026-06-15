package com.inkflow.crm.module.appointment.event;

import java.util.UUID;

public record AppointmentConfirmedEvent(UUID appointmentId, UUID tenantId) {}
