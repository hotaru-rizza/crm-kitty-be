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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final AppointmentAnalyticsQueryService appointmentAnalyticsQueryService;
    private final StaffPerformanceAnalyticsService staffPerformanceAnalyticsService;
    private final ServicePopularityAnalyticsService servicePopularityAnalyticsService;
    private final ClientAnalyticsQueryService clientAnalyticsQueryService;
    private final PnlAnalyticsService pnlAnalyticsService;

    @Transactional(readOnly = true)
    public AppointmentAnalyticsDto getAppointmentAnalytics(Instant from, Instant to, String groupBy) {
        return appointmentAnalyticsQueryService.getAppointmentAnalytics(from, to, groupBy);
    }

    @Transactional(readOnly = true)
    public List<StaffPerformanceDto> getStaffPerformance(Instant from, Instant to) {
        return staffPerformanceAnalyticsService.getStaffPerformance(from, to);
    }

    @Transactional(readOnly = true)
    public List<ServicePopularityDto> getServicePopularity(Instant from, Instant to) {
        return servicePopularityAnalyticsService.getServicePopularity(from, to);
    }

    @Transactional(readOnly = true)
    public ClientAnalyticsDto getClientAnalytics(Instant from, Instant to, String groupBy) {
        return clientAnalyticsQueryService.getClientAnalytics(from, to, groupBy);
    }

    @Transactional(readOnly = true)
    public PnlDto getPnl(Instant from, Instant to) {
        return pnlAnalyticsService.getPnl(from, to);
    }
}
