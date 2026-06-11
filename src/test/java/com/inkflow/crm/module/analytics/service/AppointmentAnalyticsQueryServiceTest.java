package com.inkflow.crm.module.analytics.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.module.analytics.dto.AppointmentAnalyticsDto;
import com.inkflow.crm.module.analytics.support.AnalyticsTimeSeriesBuilder;
import com.inkflow.crm.module.analytics.support.AppointmentMetricsCalculator;
import com.inkflow.crm.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentAnalyticsQueryServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AnalyticsTimeSeriesBuilder timeSeriesBuilder;

    @Mock
    private AppointmentMetricsCalculator metrics;

    @InjectMocks
    private AppointmentAnalyticsQueryService appointmentAnalyticsQueryService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getAppointmentAnalytics_aggregatesMetricsForTenant() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-30T23:59:59Z");

        UUID clientId = UUID.randomUUID();
        Appointment appointment = Appointment.builder()
                .tenantId(tenantId)
                .status(AppointmentStatus.DONE)
                .startTime(Instant.parse("2026-06-10T10:00:00Z"))
                .finalPrice(BigDecimal.valueOf(500))
                .client(Client.builder().id(clientId).build())
                .build();
        List<Appointment> appointments = List.of(appointment);

        when(appointmentRepository.findByTenantIdAndDateRange(tenantId, from, to)).thenReturn(appointments);
        when(metrics.countTotal(appointments)).thenReturn(1);
        when(metrics.countCompleted(appointments)).thenReturn(1);
        when(metrics.countCancelled(appointments)).thenReturn(0);
        when(metrics.countByStatus(appointments, AppointmentStatus.NEW)).thenReturn(0);
        when(metrics.countByStatus(appointments, AppointmentStatus.CONFIRMED)).thenReturn(0);
        when(metrics.sumDoneRevenue(appointments)).thenReturn(BigDecimal.valueOf(500));
        when(metrics.calculateAvgCheck(BigDecimal.valueOf(500), 1)).thenReturn(BigDecimal.valueOf(500));
        when(metrics.hasClient(appointment)).thenReturn(true);
        when(timeSeriesBuilder.buildAppointmentSeries(appointments, from, to, "day")).thenReturn(List.of());

        AppointmentAnalyticsDto result = appointmentAnalyticsQueryService.getAppointmentAnalytics(from, to, "day");

        assertEquals(1, result.getTotalAppointments());
        assertEquals(1, result.getCompletedAppointments());
        assertEquals(BigDecimal.valueOf(500), result.getTotalRevenue());
        assertEquals(1, result.getNewClients());
        assertEquals(0, result.getNewAppointments());
    }

    @Test
    void shouldExcludeAppointmentsWithoutClientFromNewClientCount() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-30T23:59:59Z");

        Appointment withoutClient = Appointment.builder()
                .status(AppointmentStatus.DONE)
                .startTime(Instant.parse("2026-06-10T10:00:00Z"))
                .finalPrice(BigDecimal.valueOf(100))
                .build();
        List<Appointment> appointments = List.of(withoutClient);

        when(appointmentRepository.findByTenantIdAndDateRange(tenantId, from, to)).thenReturn(appointments);
        when(metrics.countTotal(appointments)).thenReturn(1);
        when(metrics.countCompleted(appointments)).thenReturn(1);
        when(metrics.countCancelled(appointments)).thenReturn(0);
        when(metrics.countByStatus(appointments, AppointmentStatus.NEW)).thenReturn(0);
        when(metrics.countByStatus(appointments, AppointmentStatus.CONFIRMED)).thenReturn(0);
        when(metrics.sumDoneRevenue(appointments)).thenReturn(BigDecimal.valueOf(100));
        when(metrics.calculateAvgCheck(BigDecimal.valueOf(100), 1)).thenReturn(BigDecimal.valueOf(100));
        when(metrics.hasClient(withoutClient)).thenReturn(false);
        when(timeSeriesBuilder.buildAppointmentSeries(appointments, from, to, "day")).thenReturn(List.of());

        AppointmentAnalyticsDto result = appointmentAnalyticsQueryService.getAppointmentAnalytics(from, to, "day");

        assertEquals(0, result.getNewClients());
    }

    @Test
    void shouldCountPendingAsNewAndConfirmedStatuses() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-30T23:59:59Z");
        List<Appointment> appointments = List.of();

        when(appointmentRepository.findByTenantIdAndDateRange(tenantId, from, to)).thenReturn(appointments);
        when(metrics.countTotal(appointments)).thenReturn(0);
        when(metrics.countCompleted(appointments)).thenReturn(0);
        when(metrics.countCancelled(appointments)).thenReturn(0);
        when(metrics.countByStatus(appointments, AppointmentStatus.NEW)).thenReturn(2);
        when(metrics.countByStatus(appointments, AppointmentStatus.CONFIRMED)).thenReturn(3);
        when(metrics.sumDoneRevenue(appointments)).thenReturn(BigDecimal.ZERO);
        when(metrics.calculateAvgCheck(BigDecimal.ZERO, 0)).thenReturn(BigDecimal.ZERO);
        when(timeSeriesBuilder.buildAppointmentSeries(appointments, from, to, "day")).thenReturn(List.of());

        AppointmentAnalyticsDto result = appointmentAnalyticsQueryService.getAppointmentAnalytics(from, to, "day");

        assertEquals(5, result.getNewAppointments());
    }

    private void authenticate(UUID tenantId) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .role(com.inkflow.crm.domain.enums.UserRole.OWNER)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
