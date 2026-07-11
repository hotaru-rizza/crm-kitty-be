package com.inkflow.crm.module.analytics.service;

import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.enums.SalaryType;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.StaffScheduleRepository;
import com.inkflow.crm.module.analytics.dto.StaffPerformanceDto;
import com.inkflow.crm.module.analytics.support.AnalyticsAppointmentScope;
import com.inkflow.crm.module.analytics.support.AppointmentMetricsCalculator;
import com.inkflow.crm.module.analytics.support.CommissionCalculator;
import com.inkflow.crm.module.analytics.support.StaffUtilizationCalculator;
import com.inkflow.crm.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffPerformanceAnalyticsServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private StaffScheduleRepository staffScheduleRepository;

    @Mock
    private StaffUtilizationCalculator utilizationCalculator;

    @Mock
    private CommissionCalculator commissionCalculator;

    @Mock
    private AppointmentMetricsCalculator metrics;

    @Mock
    private AnalyticsAppointmentScope appointmentScope;

    @Mock
    private InkflowProperties inkflowProperties;

    @InjectMocks
    private StaffPerformanceAnalyticsService staffPerformanceAnalyticsService;

    @BeforeEach
    void stubZoneId() {
        lenient().when(inkflowProperties.defaultZoneId()).thenReturn(ZoneId.of("Europe/Kyiv"));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getStaffPerformance_buildsDtoPerArtist() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-30T23:59:59Z");

        UUID artistId = UUID.randomUUID();
        Staff artist = Staff.builder()
                .id(artistId)
                .firstName("Alex")
                .lastName("Ink")
                .calendarColor("#6366f1")
                .build();
        Appointment appointment = Appointment.builder()
                .status(AppointmentStatus.COMPLETED)
                .finalPrice(BigDecimal.valueOf(800))
                .artist(artist)
                .build();
        List<Appointment> appointments = List.of(appointment);

        when(appointmentScope.findForCalendarAnalytics(from, to, null)).thenReturn(appointments);
        when(metrics.hasArtist(appointment)).thenReturn(true);
        when(staffScheduleRepository.findByStaffIdIn(List.of(artistId))).thenReturn(List.of());
        when(metrics.countTotal(appointments)).thenReturn(1);
        when(metrics.countCompleted(appointments)).thenReturn(1);
        when(metrics.countCancelled(appointments)).thenReturn(0);
        when(metrics.countNoShow(appointments)).thenReturn(0);
        when(metrics.sumDoneRevenue(appointments)).thenReturn(BigDecimal.valueOf(800));
        when(metrics.calculateAvgCheck(BigDecimal.valueOf(800), 1)).thenReturn(BigDecimal.valueOf(800));
        when(commissionCalculator.resolveSalaryType(artist)).thenReturn(SalaryType.PERCENT);
        when(commissionCalculator.calculateForPeriod(eq(artist), eq(BigDecimal.valueOf(800)), eq(from), eq(to), any()))
                .thenReturn(BigDecimal.valueOf(160));
        when(utilizationCalculator.calculateScheduledHours(anyList(), eq(from), eq(to))).thenReturn(40.0);
        when(utilizationCalculator.calculateBookedHours(appointments)).thenReturn(8.0);
        when(metrics.calculateUtilizationRate(8.0, 40.0)).thenReturn(20.0);
        when(metrics.roundOneDecimal(40.0)).thenReturn(40.0);
        when(metrics.roundOneDecimal(8.0)).thenReturn(8.0);
        when(metrics.roundOneDecimal(20.0)).thenReturn(20.0);

        List<StaffPerformanceDto> result = staffPerformanceAnalyticsService.getStaffPerformance(from, to);

        assertEquals(1, result.size());
        assertEquals(artistId, result.getFirst().getStaffId());
        assertEquals("Alex Ink", result.getFirst().getName());
        assertEquals(BigDecimal.valueOf(800), result.getFirst().getRevenue());
        assertEquals(1, result.getFirst().getTotalAppointments());
        assertEquals(1, result.getFirst().getCompletedAppointments());
        assertEquals(BigDecimal.valueOf(160), result.getFirst().getCalculatedSalary());
        assertEquals(20.0, result.getFirst().getUtilizationRate());
    }

    @Test
    void shouldReturnEmptyListWhenNoAppointments() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-30T23:59:59Z");

        when(appointmentScope.findForCalendarAnalytics(from, to, null)).thenReturn(List.of());

        assertTrue(staffPerformanceAnalyticsService.getStaffPerformance(from, to).isEmpty());
    }

    @Test
    void shouldExcludeAppointmentsWithoutArtist() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-30T23:59:59Z");

        Appointment withoutArtist = Appointment.builder()
                .status(AppointmentStatus.COMPLETED)
                .finalPrice(BigDecimal.valueOf(300))
                .build();

        when(appointmentScope.findForCalendarAnalytics(from, to, null))
                .thenReturn(List.of(withoutArtist));
        when(metrics.hasArtist(withoutArtist)).thenReturn(false);

        assertTrue(staffPerformanceAnalyticsService.getStaffPerformance(from, to).isEmpty());
    }

    @Test
    void shouldSortArtistsByRevenueDescending() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-30T23:59:59Z");

        Staff lowEarner = Staff.builder().id(UUID.randomUUID()).firstName("Low").lastName("Earner").build();
        Staff topEarner = Staff.builder().id(UUID.randomUUID()).firstName("Top").lastName("Earner").build();

        Appointment low = Appointment.builder()
                .status(AppointmentStatus.COMPLETED)
                .finalPrice(BigDecimal.valueOf(200))
                .artist(lowEarner)
                .build();
        Appointment high = Appointment.builder()
                .status(AppointmentStatus.COMPLETED)
                .finalPrice(BigDecimal.valueOf(900))
                .artist(topEarner)
                .build();

        when(appointmentScope.findForCalendarAnalytics(from, to, null)).thenReturn(List.of(low, high));
        when(metrics.hasArtist(low)).thenReturn(true);
        when(metrics.hasArtist(high)).thenReturn(true);
        when(staffScheduleRepository.findByStaffIdIn(any())).thenReturn(List.of());
        when(metrics.countTotal(any())).thenReturn(1);
        when(metrics.countCompleted(any())).thenReturn(1);
        when(metrics.countCancelled(any())).thenReturn(0);
        when(metrics.countNoShow(any())).thenReturn(0);
        when(metrics.sumDoneRevenue(List.of(low))).thenReturn(BigDecimal.valueOf(200));
        when(metrics.sumDoneRevenue(List.of(high))).thenReturn(BigDecimal.valueOf(900));
        when(metrics.calculateAvgCheck(BigDecimal.valueOf(200), 1)).thenReturn(BigDecimal.valueOf(200));
        when(metrics.calculateAvgCheck(BigDecimal.valueOf(900), 1)).thenReturn(BigDecimal.valueOf(900));
        when(commissionCalculator.resolveSalaryType(any())).thenReturn(SalaryType.PERCENT);
        when(commissionCalculator.calculateForPeriod(any(), any(), eq(from), eq(to), any()))
                .thenReturn(BigDecimal.ZERO);
        when(utilizationCalculator.calculateScheduledHours(anyList(), eq(from), eq(to))).thenReturn(0.0);
        when(utilizationCalculator.calculateBookedHours(any())).thenReturn(0.0);
        when(metrics.calculateUtilizationRate(0.0, 0.0)).thenReturn(0.0);
        when(metrics.roundOneDecimal(0.0)).thenReturn(0.0);

        List<StaffPerformanceDto> result = staffPerformanceAnalyticsService.getStaffPerformance(from, to);

        assertEquals(2, result.size());
        assertEquals(topEarner.getId(), result.getFirst().getStaffId());
        assertEquals(BigDecimal.valueOf(900), result.getFirst().getRevenue());
        assertEquals(lowEarner.getId(), result.get(1).getStaffId());
        assertEquals(BigDecimal.valueOf(200), result.get(1).getRevenue());
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
