package com.inkflow.crm.module.appointment.support;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.AppointmentItem;

import java.util.Comparator;
import java.util.stream.Collectors;

public final class AppointmentLabels {

    public static final String DEFAULT_SERVICE_TITLE = "Послуга";

    private AppointmentLabels() {
    }

    public static String serviceTitle(Appointment appointment) {
        if (appointment == null) {
            return DEFAULT_SERVICE_TITLE;
        }
        if (appointment.getService() != null && appointment.getService().getTitle() != null
                && !appointment.getService().getTitle().isBlank()) {
            return appointment.getService().getTitle();
        }
        if (appointment.getItems() == null || appointment.getItems().isEmpty()) {
            return DEFAULT_SERVICE_TITLE;
        }
        String fromItems = appointment.getItems().stream()
                .sorted(Comparator.comparingInt(item -> item.getSortOrder() != null ? item.getSortOrder() : 0))
                .map(AppointmentItem::getTitle)
                .filter(title -> title != null && !title.isBlank())
                .collect(Collectors.joining(", "));
        return fromItems.isBlank() ? DEFAULT_SERVICE_TITLE : fromItems;
    }
}
