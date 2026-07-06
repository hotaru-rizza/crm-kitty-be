package com.inkflow.crm.module.google.service;

import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.module.appointment.support.AppointmentLabels;
import org.springframework.stereotype.Component;

@Component
class GoogleCalendarEventBuilder {

    Event buildEvent(Appointment appointment, String timezone) {
        String serviceName = AppointmentLabels.serviceTitle(appointment);
        String clientName = appointment.getClient() != null
                ? appointment.getClient().getFirstName() + " " + appointment.getClient().getLastName()
                : "Клієнт";

        Event event = new Event()
                .setSummary(serviceName + " — " + clientName)
                .setDescription(buildDescription(appointment));

        if (appointment.getLocation() != null && appointment.getLocation().getAddress() != null) {
            event.setLocation(appointment.getLocation().getAddress());
        }

        EventDateTime start = new EventDateTime()
                .setDateTime(new com.google.api.client.util.DateTime(
                        java.util.Date.from(appointment.getStartTime())))
                .setTimeZone(timezone);
        event.setStart(start);

        EventDateTime end = new EventDateTime()
                .setDateTime(new com.google.api.client.util.DateTime(
                        java.util.Date.from(appointment.getEndTime())))
                .setTimeZone(timezone);
        event.setEnd(end);

        return event;
    }

    private String buildDescription(Appointment appointment) {
        StringBuilder sb = new StringBuilder();
        sb.append("INKAT CRM — автоматичний запис\n\n");

        if (appointment.getClient() != null) {
            sb.append("Клієнт: ").append(appointment.getClient().getFirstName())
                    .append(" ").append(appointment.getClient().getLastName());
            if (appointment.getClient().getPhone() != null) {
                sb.append(" (").append(appointment.getClient().getPhone()).append(")");
            }
            sb.append("\n");
        }

        sb.append("Послуга: ").append(AppointmentLabels.serviceTitle(appointment)).append("\n");

        if (appointment.getPrice() != null) {
            sb.append("Вартість: ").append(appointment.getPrice()).append(" грн\n");
        }

        if (appointment.getPrepayment() != null && appointment.getPrepayment().signum() > 0) {
            sb.append("Передоплата: ").append(appointment.getPrepayment()).append(" грн\n");
        }

        if (appointment.getNotes() != null && !appointment.getNotes().isBlank()) {
            sb.append("\nНотатки: ").append(appointment.getNotes()).append("\n");
        }

        return sb.toString();
    }
}
