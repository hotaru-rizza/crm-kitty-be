package com.inkflow.crm.module.appointment.event;

import java.util.UUID;

public record AppointmentCanceledEvent(UUID appointmentId, UUID tenantId) {}
