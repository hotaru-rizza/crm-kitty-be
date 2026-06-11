package com.inkflow.crm.module.analytics;

import com.inkflow.crm.module.analytics.dto.AppointmentAnalyticsDto;
import com.inkflow.crm.module.analytics.dto.ClientAnalyticsDto;
import com.inkflow.crm.module.analytics.dto.PnlDto;
import com.inkflow.crm.module.analytics.dto.ServicePopularityDto;
import com.inkflow.crm.module.analytics.dto.StaffPerformanceDto;
import com.inkflow.crm.module.analytics.service.AppointmentAnalyticsQueryService;
import com.inkflow.crm.module.analytics.service.ClientAnalyticsQueryService;
import com.inkflow.crm.module.analytics.service.PnlAnalyticsService;
import com.inkflow.crm.module.analytics.service.ServicePopularityAnalyticsService;
import com.inkflow.crm.module.analytics.service.StaffPerformanceAnalyticsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private AppointmentAnalyticsQueryService appointmentAnalyticsQueryService;

    @Mock
    private StaffPerformanceAnalyticsService staffPerformanceAnalyticsService;

    @Mock
    private ServicePopularityAnalyticsService servicePopularityAnalyticsService;

    @Mock
    private ClientAnalyticsQueryService clientAnalyticsQueryService;

    @Mock
    private PnlAnalyticsService pnlAnalyticsService;

    @InjectMocks
    private AnalyticsService analyticsService;

    @Test
    void getAppointmentAnalytics_delegatesToQueryService() {
        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-30T23:59:59Z");
        AppointmentAnalyticsDto expected = AppointmentAnalyticsDto.builder().totalAppointments(5).build();

        when(appointmentAnalyticsQueryService.getAppointmentAnalytics(from, to, "day")).thenReturn(expected);

        assertSame(expected, analyticsService.getAppointmentAnalytics(from, to, "day"));
    }

    @Test
    void getStaffPerformance_delegatesToQueryService() {
        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-30T23:59:59Z");
        List<StaffPerformanceDto> expected = List.of(StaffPerformanceDto.builder().name("Alex").build());

        when(staffPerformanceAnalyticsService.getStaffPerformance(from, to)).thenReturn(expected);

        assertSame(expected, analyticsService.getStaffPerformance(from, to));
    }

    @Test
    void getServicePopularity_delegatesToQueryService() {
        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-30T23:59:59Z");
        List<ServicePopularityDto> expected = List.of(ServicePopularityDto.builder().name("Tattoo").build());

        when(servicePopularityAnalyticsService.getServicePopularity(from, to)).thenReturn(expected);

        assertSame(expected, analyticsService.getServicePopularity(from, to));
    }

    @Test
    void getClientAnalytics_delegatesToQueryService() {
        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-30T23:59:59Z");
        ClientAnalyticsDto expected = ClientAnalyticsDto.builder().totalUniqueClients(3).build();

        when(clientAnalyticsQueryService.getClientAnalytics(from, to, "month")).thenReturn(expected);

        assertSame(expected, analyticsService.getClientAnalytics(from, to, "month"));
    }

    @Test
    void getPnl_delegatesToQueryService() {
        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-30T23:59:59Z");
        PnlDto expected = PnlDto.builder().build();

        when(pnlAnalyticsService.getPnl(from, to)).thenReturn(expected);

        assertSame(expected, analyticsService.getPnl(from, to));
    }
}
