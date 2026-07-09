package com.inkflow.crm.module.appointment.support;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.AppointmentItem;
import com.inkflow.crm.domain.entity.Service;
import com.inkflow.crm.domain.enums.AppointmentItemSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppointmentLabelsTest {

    @Test
    void serviceTitle_usesLinkedServiceWhenPresent() {
        Appointment appointment = Appointment.builder()
                .service(Service.builder().title("Tattoo session").build())
                .build();

        assertEquals("Tattoo session", AppointmentLabels.serviceTitle(appointment));
    }

    @Test
    void serviceTitle_usesCustomItemTitlesWhenServiceMissing() {
        Appointment appointment = Appointment.builder().build();
        appointment.getItems().add(AppointmentItem.builder()
                .source(AppointmentItemSource.CUSTOM)
                .title("Миття голови")
                .unitPrice(BigDecimal.valueOf(100))
                .durationMinutes(60)
                .sortOrder(0)
                .build());

        assertEquals("Миття голови", AppointmentLabels.serviceTitle(appointment));
    }

    @Test
    void serviceTitle_joinsMultipleItemTitles() {
        Appointment appointment = Appointment.builder().build();
        appointment.getItems().add(AppointmentItem.builder()
                .source(AppointmentItemSource.SERVICE)
                .title("Tattoo")
                .sortOrder(0)
                .build());
        appointment.getItems().add(AppointmentItem.builder()
                .source(AppointmentItemSource.CUSTOM)
                .title("Aftercare")
                .sortOrder(1)
                .build());

        assertEquals("Tattoo, Aftercare", AppointmentLabels.serviceTitle(appointment));
    }

    @Test
    void serviceTitle_fallsBackWhenNothingAvailable() {
        Appointment appointment = Appointment.builder().build();

        assertEquals(AppointmentLabels.DEFAULT_SERVICE_TITLE, AppointmentLabels.serviceTitle(appointment));
    }
}
