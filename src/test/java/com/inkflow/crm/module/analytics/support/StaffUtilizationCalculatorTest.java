package com.inkflow.crm.module.analytics.support;

import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.StaffSchedule;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.enums.DayOfWeek;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StaffUtilizationCalculatorTest {

    private static final ZoneId ZONE = ZoneId.of("UTC");

    private StaffUtilizationCalculator calculator;

    @BeforeEach
    void setUp() {
        InkflowProperties properties = mock(InkflowProperties.class);
        when(properties.defaultZoneId()).thenReturn(ZONE);
        calculator = new StaffUtilizationCalculator(properties, new AppointmentMetricsCalculator());
    }

    @Test
    void shouldReturnZeroScheduledHoursWhenScheduleEmpty() {
        Instant from = instantOn("2024-06-03");
        Instant to = instantOn("2024-06-03");

        double hours = calculator.calculateScheduledHours(List.of(), from, to);

        assertEquals(0, hours);
    }

    @Test
    void shouldSumWorkingHoursAcrossMatchingDaysInRange() {
        StaffSchedule monday = workingDay(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0));
        Instant from = instantOn("2024-06-03");
        Instant to = instantOn("2024-06-05");

        double hours = calculator.calculateScheduledHours(List.of(monday), from, to);

        assertEquals(8.0, hours);
    }

    @Test
    void shouldIgnoreNonWorkingDaysInSchedule() {
        StaffSchedule dayOff = StaffSchedule.builder()
                .dayOfWeek(DayOfWeek.TUESDAY)
                .isWorking(false)
                .startTime(null)
                .endTime(null)
                .build();
        Instant from = instantOn("2024-06-04");
        Instant to = instantOn("2024-06-04");

        double hours = calculator.calculateScheduledHours(List.of(dayOff), from, to);

        assertEquals(0, hours);
    }

    @Test
    void shouldSumBookedHoursForActiveAppointmentsOnly() {
        Appointment active = timedAppointment(
                AppointmentStatus.CONFIRMED,
                Instant.parse("2024-06-03T10:00:00Z"),
                Instant.parse("2024-06-03T12:00:00Z"));
        Appointment cancelled = timedAppointment(
                AppointmentStatus.CANCELLED,
                Instant.parse("2024-06-03T14:00:00Z"),
                Instant.parse("2024-06-03T16:00:00Z"));

        double bookedHours = calculator.calculateBookedHours(List.of(active, cancelled));

        assertEquals(2.0, bookedHours);
    }

    private StaffSchedule workingDay(DayOfWeek dayOfWeek, LocalTime start, LocalTime end) {
        return StaffSchedule.builder()
                .dayOfWeek(dayOfWeek)
                .isWorking(true)
                .startTime(start)
                .endTime(end)
                .build();
    }

    private Instant instantOn(String date) {
        return java.time.LocalDate.parse(date).atStartOfDay(ZONE).toInstant();
    }

    private Appointment timedAppointment(AppointmentStatus status, Instant start, Instant end) {
        return Appointment.builder()
                .status(status)
                .startTime(start)
                .endTime(end)
                .finalPrice(BigDecimal.ZERO)
                .prepayment(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .build();
    }
}
