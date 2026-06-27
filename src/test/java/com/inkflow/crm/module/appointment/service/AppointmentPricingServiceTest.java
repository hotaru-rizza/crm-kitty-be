package com.inkflow.crm.module.appointment.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.AppointmentItem;
import com.inkflow.crm.domain.entity.Service;
import com.inkflow.crm.domain.enums.AppointmentItemSource;
import com.inkflow.crm.domain.enums.PricingType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppointmentPricingServiceTest {

    private final AppointmentPricingService pricingService = new AppointmentPricingService();

    @Test
    void suggestUnitPrice_hourlyUsesDuration() {
        Service service = Service.builder()
                .pricingType(PricingType.HOURLY)
                .price(BigDecimal.valueOf(1200))
                .build();

        BigDecimal suggested = pricingService.suggestUnitPrice(service, 90);

        assertEquals(0, suggested.compareTo(BigDecimal.valueOf(1800.00)));
    }

    @Test
    void recompute_sumsItemsAndAppliesDiscount() {
        Appointment appointment = Appointment.builder()
                .startTime(Instant.parse("2026-06-27T10:00:00Z"))
                .discount(BigDecimal.valueOf(200))
                .build();

        AppointmentItem first = AppointmentItem.builder()
                .source(AppointmentItemSource.SERVICE)
                .title("Tattoo session")
                .quantity(1)
                .unitPrice(BigDecimal.valueOf(3000))
                .durationMinutes(120)
                .sortOrder(0)
                .build();
        AppointmentItem second = AppointmentItem.builder()
                .source(AppointmentItemSource.CUSTOM)
                .title("Aftercare")
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(150))
                .durationMinutes(0)
                .sortOrder(1)
                .build();

        appointment.getItems().addAll(List.of(first, second));
        pricingService.recompute(appointment, true);

        assertEquals(0, appointment.getPrice().compareTo(BigDecimal.valueOf(3300)));
        assertEquals(0, appointment.getFinalPrice().compareTo(BigDecimal.valueOf(3100)));
        assertEquals(
                appointment.getStartTime().plus(120, ChronoUnit.MINUTES),
                appointment.getEndTime()
        );
    }
}
