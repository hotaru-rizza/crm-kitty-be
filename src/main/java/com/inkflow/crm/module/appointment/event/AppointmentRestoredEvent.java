package com.inkflow.crm.module.appointment.event;

import java.util.UUID;

public record AppointmentRestoredEvent(UUID appointmentId, UUID tenantId) {}
