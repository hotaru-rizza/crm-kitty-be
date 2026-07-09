package com.inkflow.crm.module.analytics.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.module.analytics.dto.ClientAnalyticsDto;
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

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientAnalyticsQueryServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AnalyticsTimeSeriesBuilder timeSeriesBuilder;

    @Mock
    private AppointmentMetricsCalculator metrics;

    @InjectMocks
    private ClientAnalyticsQueryService clientAnalyticsQueryService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getClientAnalytics_splitsNewAndReturningClients() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-30T23:59:59Z");

        UUID returningClientId = UUID.randomUUID();
        UUID newClientId = UUID.randomUUID();

        Appointment returning = appointment(returningClientId);
        Appointment fresh = appointment(newClientId);
        List<Appointment> inRange = List.of(returning, fresh);

        when(appointmentRepository.findByDateRange(eq(from), eq(to), isNull())).thenReturn(inRange);
        when(appointmentRepository.findByDateRange(eq(Instant.EPOCH), eq(from), isNull()))
                .thenReturn(List.of(appointment(returningClientId)));
        when(metrics.hasClient(any())).thenReturn(true);
        when(metrics.calculateRepeatRate(1, 2)).thenReturn(50.0);
        when(timeSeriesBuilder.buildClientSeries(inRange, Set.of(returningClientId), from, to, "month"))
                .thenReturn(List.of());

        ClientAnalyticsDto result = clientAnalyticsQueryService.getClientAnalytics(from, to, "month");

        assertEquals(2, result.getTotalUniqueClients());
        assertEquals(1, result.getNewClients());
        assertEquals(1, result.getReturningClients());
        assertEquals(50.0, result.getRepeatRate());
    }

    @Test
    void shouldReturnZerosWhenNoAppointmentsInRange() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-30T23:59:59Z");

        when(appointmentRepository.findByDateRange(eq(from), eq(to), isNull())).thenReturn(List.of());
        when(appointmentRepository.findByDateRange(eq(Instant.EPOCH), eq(from), isNull()))
                .thenReturn(List.of());
        when(metrics.calculateRepeatRate(0, 0)).thenReturn(0.0);
        when(timeSeriesBuilder.buildClientSeries(List.of(), Set.of(), from, to, "month")).thenReturn(List.of());

        ClientAnalyticsDto result = clientAnalyticsQueryService.getClientAnalytics(from, to, "month");

        assertEquals(0, result.getTotalUniqueClients());
        assertEquals(0, result.getNewClients());
        assertEquals(0, result.getReturningClients());
        assertEquals(0.0, result.getRepeatRate());
    }

    @Test
    void shouldExcludeAppointmentsWithoutClientFromUniqueCount() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-30T23:59:59Z");

        Appointment withoutClient = Appointment.builder().build();
        Appointment withClient = appointment(UUID.randomUUID());
        List<Appointment> inRange = List.of(withoutClient, withClient);

        when(appointmentRepository.findByDateRange(eq(from), eq(to), isNull())).thenReturn(inRange);
        when(appointmentRepository.findByDateRange(eq(Instant.EPOCH), eq(from), isNull()))
                .thenReturn(List.of());
        when(metrics.hasClient(withoutClient)).thenReturn(false);
        when(metrics.hasClient(withClient)).thenReturn(true);
        when(metrics.calculateRepeatRate(0, 1)).thenReturn(0.0);
        when(timeSeriesBuilder.buildClientSeries(inRange, Set.of(), from, to, "month")).thenReturn(List.of());

        ClientAnalyticsDto result = clientAnalyticsQueryService.getClientAnalytics(from, to, "month");

        assertEquals(1, result.getTotalUniqueClients());
        assertEquals(1, result.getNewClients());
        assertEquals(0, result.getReturningClients());
    }

    @Test
    void shouldCountDuplicateVisitsForSameClientOnce() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-30T23:59:59Z");
        UUID clientId = UUID.randomUUID();

        List<Appointment> inRange = List.of(appointment(clientId), appointment(clientId));

        when(appointmentRepository.findByDateRange(eq(from), eq(to), isNull())).thenReturn(inRange);
        when(appointmentRepository.findByDateRange(eq(Instant.EPOCH), eq(from), isNull()))
                .thenReturn(List.of());
        when(metrics.hasClient(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(metrics.calculateRepeatRate(0, 1)).thenReturn(0.0);
        when(timeSeriesBuilder.buildClientSeries(inRange, Set.of(), from, to, "month")).thenReturn(List.of());

        ClientAnalyticsDto result = clientAnalyticsQueryService.getClientAnalytics(from, to, "month");

        assertEquals(1, result.getTotalUniqueClients());
        assertEquals(1, result.getNewClients());
    }

    @Test
    void shouldTreatAllClientsAsNewWhenNoPriorHistory() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-30T23:59:59Z");

        UUID clientA = UUID.randomUUID();
        UUID clientB = UUID.randomUUID();
        List<Appointment> inRange = List.of(appointment(clientA), appointment(clientB));

        when(appointmentRepository.findByDateRange(eq(from), eq(to), isNull())).thenReturn(inRange);
        when(appointmentRepository.findByDateRange(eq(Instant.EPOCH), eq(from), isNull()))
                .thenReturn(List.of());
        when(metrics.hasClient(any())).thenReturn(true);
        when(metrics.calculateRepeatRate(0, 2)).thenReturn(0.0);
        when(timeSeriesBuilder.buildClientSeries(inRange, Set.of(), from, to, "month")).thenReturn(List.of());

        ClientAnalyticsDto result = clientAnalyticsQueryService.getClientAnalytics(from, to, "month");

        assertEquals(2, result.getTotalUniqueClients());
        assertEquals(2, result.getNewClients());
        assertEquals(0, result.getReturningClients());
    }

    private Appointment appointment(UUID clientId) {
        return Appointment.builder()
                .client(Client.builder().id(clientId).build())
                .build();
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
