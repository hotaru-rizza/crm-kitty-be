package com.inkflow.crm.module.analytics.support;

import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.module.analytics.dto.AppointmentAnalyticsDto;
import com.inkflow.crm.module.analytics.dto.ClientAnalyticsDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyticsTimeSeriesBuilderTest {

    private static final ZoneId ZONE = ZoneId.of("UTC");

    private AnalyticsTimeSeriesBuilder builder;

    @BeforeEach
    void setUp() {
        InkflowProperties properties = mock(InkflowProperties.class);
        when(properties.defaultZoneId()).thenReturn(ZONE);
        builder = new AnalyticsTimeSeriesBuilder(properties, new AppointmentMetricsCalculator());
    }

    @Test
    void shouldBuildDailyAppointmentSeriesWithNumericAggregates() {
        Instant from = instantOn("2024-06-03");
        Instant to = instantOn("2024-06-04");

        Appointment dayOneDone = timedAppointment(
                AppointmentStatus.DONE,
                instantAt("2024-06-03T11:00:00Z"),
                BigDecimal.valueOf(500));
        Appointment dayOneCancelled = timedAppointment(
                AppointmentStatus.CANCELLED,
                instantAt("2024-06-03T15:00:00Z"),
                BigDecimal.valueOf(200));
        Appointment dayTwoDone = timedAppointment(
                AppointmentStatus.DONE,
                instantAt("2024-06-04T10:00:00Z"),
                BigDecimal.valueOf(300));

        List<AppointmentAnalyticsDto.DataPoint> series = builder.buildAppointmentSeries(
                List.of(dayOneDone, dayOneCancelled, dayTwoDone),
                from,
                to,
                "day");

        assertEquals(2, series.size());
        assertEquals("2024-06-03", series.get(0).getDate());
        assertEquals(2, series.get(0).getTotal());
        assertEquals(1, series.get(0).getCompleted());
        assertEquals(1, series.get(0).getCancelled());
        assertEquals(BigDecimal.valueOf(500), series.get(0).getRevenue());
        assertEquals("2024-06-04", series.get(1).getDate());
        assertEquals(1, series.get(1).getTotal());
        assertEquals(1, series.get(1).getCompleted());
        assertEquals(0, series.get(1).getCancelled());
        assertEquals(BigDecimal.valueOf(300), series.get(1).getRevenue());
    }

    @Test
    void shouldBuildWeeklySeriesGroupingByIsoWeek() {
        Instant from = instantOn("2024-06-03");
        Instant to = instantOn("2024-06-09");
        Appointment done = timedAppointment(
                AppointmentStatus.DONE,
                instantAt("2024-06-05T12:00:00Z"),
                BigDecimal.valueOf(750));

        List<AppointmentAnalyticsDto.DataPoint> series = builder.buildAppointmentSeries(
                List.of(done),
                from,
                to,
                "week");

        assertEquals(1, series.size());
        assertEquals("2024-W23", series.getFirst().getDate());
        assertEquals(1, series.getFirst().getCompleted());
        assertEquals(BigDecimal.valueOf(750), series.getFirst().getRevenue());
    }

    @Test
    void shouldBuildMonthlySeriesGroupingByCalendarMonth() {
        Instant from = instantOn("2024-06-01");
        Instant to = instantOn("2024-06-30");
        Appointment done = timedAppointment(
                AppointmentStatus.DONE,
                instantAt("2024-06-15T09:00:00Z"),
                BigDecimal.valueOf(1200));

        List<AppointmentAnalyticsDto.DataPoint> series = builder.buildAppointmentSeries(
                List.of(done),
                from,
                to,
                "month");

        assertEquals(1, series.size());
        assertEquals("2024-06", series.getFirst().getDate());
        assertEquals(1, series.getFirst().getTotal());
        assertEquals(BigDecimal.valueOf(1200), series.getFirst().getRevenue());
    }

    @Test
    void shouldBuildClientSeriesDistinguishingNewAndReturningClients() {
        UUID returningClientId = UUID.randomUUID();
        UUID newClientId = UUID.randomUUID();
        Instant from = instantOn("2024-06-03");
        Instant to = instantOn("2024-06-03");

        Appointment returning = timedAppointmentWithClient(
                instantAt("2024-06-03T10:00:00Z"),
                Client.builder().id(returningClientId).build());
        Appointment newcomer = timedAppointmentWithClient(
                instantAt("2024-06-03T14:00:00Z"),
                Client.builder().id(newClientId).build());

        List<ClientAnalyticsDto.DataPoint> series = builder.buildClientSeries(
                List.of(returning, newcomer),
                Set.of(returningClientId),
                from,
                to,
                "day");

        assertEquals(1, series.size());
        assertEquals("2024-06-03", series.getFirst().getDate());
        assertEquals(1, series.getFirst().getNewClients());
        assertEquals(1, series.getFirst().getReturningClients());
        assertEquals(2, series.getFirst().getTotal());
    }

    private Instant instantOn(String date) {
        return java.time.LocalDate.parse(date).atStartOfDay(ZONE).toInstant();
    }

    private Instant instantAt(String isoInstant) {
        return Instant.parse(isoInstant);
    }

    private Appointment timedAppointment(AppointmentStatus status, Instant startTime, BigDecimal finalPrice) {
        return Appointment.builder()
                .status(status)
                .startTime(startTime)
                .endTime(startTime.plusSeconds(3600))
                .finalPrice(finalPrice)
                .prepayment(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .build();
    }

    private Appointment timedAppointmentWithClient(Instant startTime, Client client) {
        Appointment appointment = timedAppointment(AppointmentStatus.DONE, startTime, BigDecimal.TEN);
        appointment.setClient(client);
        return appointment;
    }
}
