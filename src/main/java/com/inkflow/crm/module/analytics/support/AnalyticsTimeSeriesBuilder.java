package com.inkflow.crm.module.analytics.support;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.module.analytics.dto.AppointmentAnalyticsDto;
import com.inkflow.crm.module.analytics.dto.ClientAnalyticsDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AnalyticsTimeSeriesBuilder {

    private static final ZoneId ANALYTICS_ZONE = ZoneId.of("Europe/Kyiv");

    private final AppointmentMetricsCalculator metrics;

    public List<AppointmentAnalyticsDto.DataPoint> buildAppointmentSeries(
            List<Appointment> appointments,
            Instant from,
            Instant to,
            String groupBy) {
        Map<String, List<Appointment>> buckets = createBuckets(from, to, groupBy);
        fillBuckets(buckets, appointments, groupBy);

        return buckets.entrySet().stream()
                .map(entry -> toAppointmentDataPoint(entry.getKey(), entry.getValue()))
                .toList();
    }

    public List<ClientAnalyticsDto.DataPoint> buildClientSeries(
            List<Appointment> appointments,
            Set<UUID> existingClientIds,
            Instant from,
            Instant to,
            String groupBy) {
        Map<String, List<Appointment>> buckets = createBuckets(from, to, groupBy);
        fillBuckets(buckets, appointments, groupBy);

        return buckets.entrySet().stream()
                .map(entry -> toClientDataPoint(entry.getKey(), entry.getValue(), existingClientIds))
                .toList();
    }

    private AppointmentAnalyticsDto.DataPoint toAppointmentDataPoint(String date, List<Appointment> bucket) {
        int total = metrics.countTotal(bucket);
        int completed = metrics.countCompleted(bucket);
        int cancelled = metrics.countCancelled(bucket);
        BigDecimal revenue = metrics.sumDoneRevenue(bucket);

        return AppointmentAnalyticsDto.DataPoint.builder()
                .date(date)
                .total(total)
                .completed(completed)
                .cancelled(cancelled)
                .revenue(revenue)
                .build();
    }

    private ClientAnalyticsDto.DataPoint toClientDataPoint(
            String date,
            List<Appointment> bucket,
            Set<UUID> existingClientIds) {
        Set<UUID> clientIdsInBucket = collectClientIds(bucket);
        int newClients = countClientsNotIn(clientIdsInBucket, existingClientIds);
        int returningClients = countClientsIn(clientIdsInBucket, existingClientIds);

        return ClientAnalyticsDto.DataPoint.builder()
                .date(date)
                .newClients(newClients)
                .returningClients(returningClients)
                .total(newClients + returningClients)
                .build();
    }

    private Set<UUID> collectClientIds(List<Appointment> appointments) {
        return appointments.stream()
                .filter(metrics::hasClient)
                .map(appointment -> appointment.getClient().getId())
                .collect(Collectors.toSet());
    }

    private int countClientsNotIn(Set<UUID> clientIds, Set<UUID> excludedIds) {
        return (int) clientIds.stream()
                .filter(clientId -> !excludedIds.contains(clientId))
                .count();
    }

    private int countClientsIn(Set<UUID> clientIds, Set<UUID> includedIds) {
        return (int) clientIds.stream()
                .filter(includedIds::contains)
                .count();
    }

    private Map<String, List<Appointment>> createBuckets(Instant from, Instant to, String groupBy) {
        LocalDate startDate = from.atZone(ANALYTICS_ZONE).toLocalDate();
        LocalDate endDate = to.atZone(ANALYTICS_ZONE).toLocalDate();
        Map<String, List<Appointment>> buckets = new LinkedHashMap<>();

        if ("week".equals(groupBy)) {
            populateWeekBuckets(buckets, startDate, endDate);
        } else if ("month".equals(groupBy)) {
            populateMonthBuckets(buckets, startDate, endDate);
        } else {
            populateDayBuckets(buckets, startDate, endDate);
        }

        return buckets;
    }

    private void populateWeekBuckets(Map<String, List<Appointment>> buckets, LocalDate startDate, LocalDate endDate) {
        DateTimeFormatter weekFormatter = DateTimeFormatter.ofPattern("yyyy-'W'ww");
        LocalDate cursor = startDate.with(java.time.DayOfWeek.MONDAY);

        while (!cursor.isAfter(endDate)) {
            buckets.put(cursor.format(weekFormatter), new ArrayList<>());
            cursor = cursor.plusWeeks(1);
        }
    }

    private void populateMonthBuckets(Map<String, List<Appointment>> buckets, LocalDate startDate, LocalDate endDate) {
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM");
        LocalDate cursor = startDate.withDayOfMonth(1);

        while (!cursor.isAfter(endDate)) {
            buckets.put(cursor.format(monthFormatter), new ArrayList<>());
            cursor = cursor.plusMonths(1);
        }
    }

    private void populateDayBuckets(Map<String, List<Appointment>> buckets, LocalDate startDate, LocalDate endDate) {
        DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        long dayCount = Math.min(ChronoUnit.DAYS.between(startDate, endDate) + 1, 90);

        for (long dayOffset = 0; dayOffset < dayCount; dayOffset++) {
            buckets.put(startDate.plusDays(dayOffset).format(dayFormatter), new ArrayList<>());
        }
    }

    private void fillBuckets(
            Map<String, List<Appointment>> buckets,
            List<Appointment> appointments,
            String groupBy) {
        for (Appointment appointment : appointments) {
            String bucketKey = resolveBucketKey(appointment, groupBy);
            buckets.computeIfPresent(bucketKey, (key, bucket) -> {
                bucket.add(appointment);
                return bucket;
            });
        }
    }

    private String resolveBucketKey(Appointment appointment, String groupBy) {
        LocalDate appointmentDate = appointment.getStartTime().atZone(ANALYTICS_ZONE).toLocalDate();

        return switch (groupBy) {
            case "week" -> appointmentDate
                    .with(java.time.DayOfWeek.MONDAY)
                    .format(DateTimeFormatter.ofPattern("yyyy-'W'ww"));
            case "month" -> appointmentDate
                    .withDayOfMonth(1)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM"));
            default -> appointmentDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        };
    }
}
