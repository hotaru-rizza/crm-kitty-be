package com.inkflow.crm.module.analytics.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.module.analytics.dto.ClientAnalyticsDto;
import com.inkflow.crm.module.analytics.support.AnalyticsTimeSeriesBuilder;
import com.inkflow.crm.module.analytics.support.AppointmentMetricsCalculator;
import com.inkflow.crm.security.LocationScope;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClientAnalyticsQueryService {

    private final AppointmentRepository appointmentRepository;
    private final AnalyticsTimeSeriesBuilder timeSeriesBuilder;
    private final AppointmentMetricsCalculator metrics;

    @Transactional(readOnly = true)
    public ClientAnalyticsDto getClientAnalytics(Instant from, Instant to, String groupBy) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID locationId = LocationScope.resolveFilter(null).orElse(null);
        List<Appointment> appointments = appointmentRepository.findByDateRange(from, to, locationId);

        Set<UUID> clientIdsInRange = collectClientIds(appointments);
        Set<UUID> existingClientIds = loadExistingClientIds(from, locationId);

        int newClients = countNewClients(clientIdsInRange, existingClientIds);
        int returningClients = countReturningClients(clientIdsInRange, existingClientIds);
        int totalUnique = clientIdsInRange.size();
        double repeatRate = metrics.calculateRepeatRate(returningClients, totalUnique);

        return ClientAnalyticsDto.builder()
                .totalUniqueClients(totalUnique)
                .newClients(newClients)
                .returningClients(returningClients)
                .repeatRate(repeatRate)
                .series(timeSeriesBuilder.buildClientSeries(appointments, existingClientIds, from, to, groupBy))
                .build();
    }

    private Set<UUID> collectClientIds(List<Appointment> appointments) {
        return appointments.stream()
                .filter(metrics::hasClient)
                .map(appointment -> appointment.getClient().getId())
                .collect(Collectors.toSet());
    }

    private Set<UUID> loadExistingClientIds(Instant before, UUID locationId) {
        return appointmentRepository
                .findByDateRange(Instant.EPOCH, before, locationId)
                .stream()
                .filter(metrics::hasClient)
                .map(appointment -> appointment.getClient().getId())
                .collect(Collectors.toSet());
    }

    private int countNewClients(Set<UUID> clientIdsInRange, Set<UUID> existingClientIds) {
        return (int) clientIdsInRange.stream()
                .filter(clientId -> !existingClientIds.contains(clientId))
                .count();
    }

    private int countReturningClients(Set<UUID> clientIdsInRange, Set<UUID> existingClientIds) {
        return (int) clientIdsInRange.stream()
                .filter(existingClientIds::contains)
                .count();
    }
}
