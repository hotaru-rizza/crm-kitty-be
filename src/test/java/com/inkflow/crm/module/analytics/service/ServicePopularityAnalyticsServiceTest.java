package com.inkflow.crm.module.analytics.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Service;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.module.analytics.dto.ServicePopularityDto;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServicePopularityAnalyticsServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AppointmentMetricsCalculator metrics;

    @InjectMocks
    private ServicePopularityAnalyticsService servicePopularityAnalyticsService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getServicePopularity_buildsDtoPerService() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-30T23:59:59Z");

        UUID serviceId = UUID.randomUUID();
        Service service = Service.builder()
                .id(serviceId)
                .title("Small Tattoo")
                .costPrice(BigDecimal.valueOf(50))
                .build();
        Appointment appointment = Appointment.builder()
                .status(AppointmentStatus.COMPLETED)
                .finalPrice(BigDecimal.valueOf(300))
                .service(service)
                .build();
        List<Appointment> appointments = List.of(appointment);

        when(appointmentRepository.findByTenantIdAndDateRange(tenantId, from, to)).thenReturn(appointments);
        when(metrics.hasService(appointment)).thenReturn(true);
        when(metrics.countTotal(appointments)).thenReturn(1);
        when(metrics.countCompleted(appointments)).thenReturn(1);
        when(metrics.countCancelled(appointments)).thenReturn(0);
        when(metrics.sumDoneRevenue(appointments)).thenReturn(BigDecimal.valueOf(300));
        when(metrics.calculateAvgCheck(BigDecimal.valueOf(300), 1)).thenReturn(BigDecimal.valueOf(300));
        when(metrics.calculateCompletionRate(1, 1)).thenReturn(BigDecimal.valueOf(100));

        List<ServicePopularityDto> result = servicePopularityAnalyticsService.getServicePopularity(from, to);

        assertEquals(1, result.size());
        assertEquals(serviceId, result.getFirst().getServiceId());
        assertEquals("Small Tattoo", result.getFirst().getName());
        assertEquals(BigDecimal.valueOf(300), result.getFirst().getRevenue());
        assertEquals(BigDecimal.valueOf(100), result.getFirst().getCompletionRate());
    }

    @Test
    void shouldReturnEmptyListWhenNoAppointments() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-30T23:59:59Z");

        when(appointmentRepository.findByTenantIdAndDateRange(tenantId, from, to)).thenReturn(List.of());

        assertTrue(servicePopularityAnalyticsService.getServicePopularity(from, to).isEmpty());
    }

    @Test
    void shouldExcludeAppointmentsWithoutService() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-30T23:59:59Z");

        Appointment withoutService = Appointment.builder()
                .status(AppointmentStatus.COMPLETED)
                .finalPrice(BigDecimal.valueOf(150))
                .build();

        when(appointmentRepository.findByTenantIdAndDateRange(tenantId, from, to))
                .thenReturn(List.of(withoutService));
        when(metrics.hasService(withoutService)).thenReturn(false);

        assertTrue(servicePopularityAnalyticsService.getServicePopularity(from, to).isEmpty());
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
