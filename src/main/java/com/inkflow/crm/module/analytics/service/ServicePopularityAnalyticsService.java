package com.inkflow.crm.module.analytics.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Service;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.module.analytics.dto.ServicePopularityDto;
import com.inkflow.crm.module.analytics.support.AnalyticsAppointmentScope;
import com.inkflow.crm.module.analytics.support.AppointmentMetricsCalculator;
import com.inkflow.crm.security.LocationScope;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class ServicePopularityAnalyticsService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentMetricsCalculator metrics;
    private final AnalyticsAppointmentScope appointmentScope;

    @Transactional(readOnly = true)
    public List<ServicePopularityDto> getServicePopularity(Instant from, Instant to) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID locationId = LocationScope.resolveFilter(null).orElse(null);
        List<Appointment> appointments = appointmentScope.findForCalendarAnalytics(from, to, locationId);

        return groupAppointmentsByService(appointments).values().stream()
                .map(this::toPopularityDto)
                .sorted(Comparator.comparingInt(ServicePopularityDto::getTotalAppointments).reversed())
                .toList();
    }

    private Map<UUID, List<Appointment>> groupAppointmentsByService(List<Appointment> appointments) {
        return appointments.stream()
                .filter(metrics::hasService)
                .collect(Collectors.groupingBy(appointment -> appointment.getService().getId()));
    }

    private ServicePopularityDto toPopularityDto(List<Appointment> serviceAppointments) {
        Service service = serviceAppointments.getFirst().getService();

        int total = metrics.countTotal(serviceAppointments);
        int completed = metrics.countCompleted(serviceAppointments);
        int cancelled = metrics.countCancelled(serviceAppointments);
        BigDecimal revenue = metrics.sumDoneRevenue(serviceAppointments);
        BigDecimal avgCheck = metrics.calculateAvgCheck(revenue, completed);
        BigDecimal completionRate = metrics.calculateCompletionRate(completed, total);

        return ServicePopularityDto.builder()
                .serviceId(service.getId())
                .name(service.getTitle())
                .totalAppointments(total)
                .completedAppointments(completed)
                .cancelledAppointments(cancelled)
                .revenue(revenue)
                .avgCheck(avgCheck)
                .completionRate(completionRate)
                .costPrice(service.getCostPrice())
                .build();
    }
}
