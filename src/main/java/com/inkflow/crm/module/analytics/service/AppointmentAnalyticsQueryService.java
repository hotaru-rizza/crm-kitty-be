package com.inkflow.crm.module.analytics.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.module.analytics.dto.AppointmentAnalyticsDto;
import com.inkflow.crm.module.analytics.support.AnalyticsTimeSeriesBuilder;
import com.inkflow.crm.module.analytics.support.AppointmentMetricsCalculator;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AppointmentAnalyticsQueryService {

    private final AppointmentRepository appointmentRepository;
    private final AnalyticsTimeSeriesBuilder timeSeriesBuilder;
    private final AppointmentMetricsCalculator metrics;

    @Transactional(readOnly = true)
    public AppointmentAnalyticsDto getAppointmentAnalytics(Instant from, Instant to, String groupBy) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        List<Appointment> appointments = appointmentRepository.findByTenantIdAndDateRange(tenantId, from, to);

        int total = metrics.countTotal(appointments);
        int completed = metrics.countCompleted(appointments);
        int cancelled = metrics.countCancelled(appointments);
        int pending = countPendingAppointments(appointments);

        BigDecimal totalRevenue = metrics.sumDoneRevenue(appointments);
        BigDecimal avgCheck = metrics.calculateAvgCheck(totalRevenue, completed);
        int newClients = (int) countNewClientsInRange(appointments, from, to);

        return AppointmentAnalyticsDto.builder()
                .series(timeSeriesBuilder.buildAppointmentSeries(appointments, from, to, groupBy))
                .totalAppointments(total)
                .completedAppointments(completed)
                .cancelledAppointments(cancelled)
                .newAppointments(pending)
                .totalRevenue(totalRevenue)
                .avgCheck(avgCheck)
                .newClients(newClients)
                .build();
    }

    private int countPendingAppointments(List<Appointment> appointments) {
        return metrics.countByStatus(appointments, AppointmentStatus.SCHEDULED);
    }

    private long countNewClientsInRange(List<Appointment> appointments, Instant from, Instant to) {
        Map<UUID, Instant> firstAppointmentByClient = buildFirstAppointmentIndex(appointments);

        return firstAppointmentByClient.values().stream()
                .filter(startTime -> isWithinRange(startTime, from, to))
                .count();
    }

    private Map<UUID, Instant> buildFirstAppointmentIndex(List<Appointment> appointments) {
        Map<UUID, Instant> firstAppointmentByClient = new HashMap<>();

        for (Appointment appointment : appointments) {
            if (!metrics.hasClient(appointment)) {
                continue;
            }

            UUID clientId = appointment.getClient().getId();
            Instant startTime = appointment.getStartTime();
            firstAppointmentByClient.merge(clientId, startTime, this::earlierInstant);
        }

        return firstAppointmentByClient;
    }

    private Instant earlierInstant(Instant existing, Instant candidate) {
        return candidate.isBefore(existing) ? candidate : existing;
    }

    private boolean isWithinRange(Instant time, Instant from, Instant to) {
        return !time.isBefore(from) && time.isBefore(to);
    }
}
