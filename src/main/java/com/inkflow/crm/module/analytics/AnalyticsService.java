package com.inkflow.crm.module.analytics;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.entity.StaffSchedule;
import com.inkflow.crm.domain.entity.TransactionCategoryConfig;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.enums.TransactionCategory;
import com.inkflow.crm.domain.enums.TransactionType;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.StaffScheduleRepository;
import com.inkflow.crm.domain.repository.TransactionCategoryConfigRepository;
import com.inkflow.crm.domain.repository.TransactionRepository;
import com.inkflow.crm.module.analytics.dto.AppointmentAnalyticsDto;
import com.inkflow.crm.module.analytics.dto.ClientAnalyticsDto;
import com.inkflow.crm.module.analytics.dto.PnlDto;
import com.inkflow.crm.module.analytics.dto.ServicePopularityDto;
import com.inkflow.crm.module.analytics.dto.StaffPerformanceDto;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final AppointmentRepository appointmentRepository;
    private final ClientRepository clientRepository;
    private final StaffScheduleRepository staffScheduleRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionCategoryConfigRepository categoryConfigRepository;

    @Transactional(readOnly = true)
    public AppointmentAnalyticsDto getAppointmentAnalytics(Instant from, Instant to, String groupBy) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        List<Appointment> appointments = appointmentRepository.findByTenantIdAndDateRange(tenantId, from, to);

        int total = appointments.size();
        int completed = (int) appointments.stream().filter(a -> a.getStatus() == AppointmentStatus.DONE).count();
        int cancelled = (int) appointments.stream().filter(a -> a.getStatus() == AppointmentStatus.CANCELLED).count();
        int newApp = (int) appointments.stream().filter(a -> a.getStatus() == AppointmentStatus.NEW || a.getStatus() == AppointmentStatus.CONFIRMED).count();

        BigDecimal totalRevenue = appointments.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.DONE)
                .map(a -> a.getFinalPrice() != null ? a.getFinalPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgCheck = completed > 0
                ? totalRevenue.divide(BigDecimal.valueOf(completed), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Count new clients: clients whose first appointment (by start time) falls in this range
        Map<UUID, Instant> firstAppointmentByClient = new HashMap<>();
        appointments.forEach(a -> {
            if (a.getClient() != null) {
                UUID clientId = a.getClient().getId();
                firstAppointmentByClient.merge(clientId, a.getStartTime(),
                        (existing, newTime) -> newTime.isBefore(existing) ? newTime : existing);
            }
        });
        // Count clients where the earliest appointment we know of is in this range
        long newClients = firstAppointmentByClient.values().stream()
                .filter(t -> !t.isBefore(from) && t.isBefore(to))
                .count();

        // Build time series
        List<AppointmentAnalyticsDto.DataPoint> series = buildSeries(appointments, from, to, groupBy);

        return AppointmentAnalyticsDto.builder()
                .series(series)
                .totalAppointments(total)
                .completedAppointments(completed)
                .cancelledAppointments(cancelled)
                .newAppointments(newApp)
                .totalRevenue(totalRevenue)
                .avgCheck(avgCheck)
                .newClients((int) newClients)
                .build();
    }

    @Transactional(readOnly = true)
    public List<StaffPerformanceDto> getStaffPerformance(Instant from, Instant to) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        List<Appointment> appointments = appointmentRepository.findByTenantIdAndDateRange(tenantId, from, to);

        Map<UUID, List<Appointment>> byArtist = appointments.stream()
                .filter(a -> a.getArtist() != null)
                .collect(Collectors.groupingBy(a -> a.getArtist().getId()));

        List<UUID> artistIds = new ArrayList<>(byArtist.keySet());
        Map<UUID, List<StaffSchedule>> schedulesByArtist = staffScheduleRepository
                .findByStaffIdIn(artistIds)
                .stream()
                .collect(Collectors.groupingBy(s -> s.getStaff().getId()));

        return byArtist.entrySet().stream().map(entry -> {
            List<Appointment> artistApps = entry.getValue();
            Staff artist = artistApps.get(0).getArtist();

            int total = artistApps.size();
            int completed = (int) artistApps.stream().filter(a -> a.getStatus() == AppointmentStatus.DONE).count();
            int cancelled = (int) artistApps.stream().filter(a -> a.getStatus() == AppointmentStatus.CANCELLED).count();

            BigDecimal revenue = artistApps.stream()
                    .filter(a -> a.getStatus() == AppointmentStatus.DONE)
                    .map(a -> a.getFinalPrice() != null ? a.getFinalPrice() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal avgCheck = completed > 0
                    ? revenue.divide(BigDecimal.valueOf(completed), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            com.inkflow.crm.domain.enums.SalaryType salaryType = artist.getSalaryType() != null
                    ? artist.getSalaryType()
                    : com.inkflow.crm.domain.enums.SalaryType.NONE;
            BigDecimal salaryRate = artist.getSalaryRate();
            BigDecimal calculatedSalary = BigDecimal.ZERO;
            if (salaryType == com.inkflow.crm.domain.enums.SalaryType.PERCENT && salaryRate != null) {
                calculatedSalary = revenue.multiply(salaryRate)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            } else if (salaryType == com.inkflow.crm.domain.enums.SalaryType.FIXED && salaryRate != null) {
                calculatedSalary = salaryRate;
            }

            List<StaffSchedule> artistSched = schedulesByArtist.getOrDefault(artist.getId(), List.of());
            double scheduledHours = calculateScheduledHours(artistSched, from, to);
            double bookedHours = calculateBookedHours(artistApps);
            double utilizationRate = scheduledHours > 0
                    ? Math.min(bookedHours / scheduledHours * 100, 100)
                    : 0;

            return StaffPerformanceDto.builder()
                    .staffId(artist.getId())
                    .name(artist.getFirstName() + " " + artist.getLastName())
                    .avatar(artist.getAvatar())
                    .calendarColor(artist.getCalendarColor())
                    .totalAppointments(total)
                    .completedAppointments(completed)
                    .cancelledAppointments(cancelled)
                    .revenue(revenue)
                    .avgCheck(avgCheck)
                    .salaryType(salaryType.getValue())
                    .salaryRate(salaryRate)
                    .calculatedSalary(calculatedSalary)
                    .scheduledHours(Math.round(scheduledHours * 10.0) / 10.0)
                    .bookedHours(Math.round(bookedHours * 10.0) / 10.0)
                    .utilizationRate(Math.round(utilizationRate * 10.0) / 10.0)
                    .build();
        })
        .sorted(Comparator.comparing(StaffPerformanceDto::getRevenue).reversed())
        .collect(Collectors.toList());
    }

    private double calculateScheduledHours(List<StaffSchedule> schedule, Instant from, Instant to) {
        if (schedule.isEmpty()) return 0;
        Map<com.inkflow.crm.domain.enums.DayOfWeek, StaffSchedule> schedMap = schedule.stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsWorking())
                        && s.getStartTime() != null && s.getEndTime() != null)
                .collect(Collectors.toMap(StaffSchedule::getDayOfWeek, s -> s, (a, b) -> a));

        ZoneId zone = ZoneId.of("Europe/Kyiv");
        LocalDate startDate = from.atZone(zone).toLocalDate();
        LocalDate endDate = to.atZone(zone).toLocalDate();

        double total = 0;
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            com.inkflow.crm.domain.enums.DayOfWeek dow =
                    com.inkflow.crm.domain.enums.DayOfWeek.fromJavaDayOfWeek(cursor.getDayOfWeek());
            StaffSchedule s = schedMap.get(dow);
            if (s != null) {
                total += java.time.Duration.between(s.getStartTime(), s.getEndTime()).toMinutes() / 60.0;
            }
            cursor = cursor.plusDays(1);
        }
        return total;
    }

    private double calculateBookedHours(List<Appointment> appointments) {
        return appointments.stream()
                .filter(a -> a.getStatus() != AppointmentStatus.CANCELLED)
                .mapToLong(a -> java.time.Duration.between(a.getStartTime(), a.getEndTime()).toMinutes())
                .sum() / 60.0;
    }

    @Transactional(readOnly = true)
    public List<ServicePopularityDto> getServicePopularity(Instant from, Instant to) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        List<Appointment> appointments = appointmentRepository.findByTenantIdAndDateRange(tenantId, from, to);

        Map<UUID, List<Appointment>> byService = appointments.stream()
                .filter(a -> a.getService() != null)
                .collect(Collectors.groupingBy(a -> a.getService().getId()));

        List<ServicePopularityDto> result = new ArrayList<>();
        for (Map.Entry<UUID, List<Appointment>> entry : byService.entrySet()) {
            List<Appointment> svcApps = entry.getValue();
            com.inkflow.crm.domain.entity.Service service = svcApps.get(0).getService();

            int total = svcApps.size();
            int completed = (int) svcApps.stream().filter(a -> a.getStatus() == AppointmentStatus.DONE).count();
            int cancelled = (int) svcApps.stream().filter(a -> a.getStatus() == AppointmentStatus.CANCELLED).count();

            BigDecimal revenue = svcApps.stream()
                    .filter(a -> a.getStatus() == AppointmentStatus.DONE)
                    .map(a -> a.getFinalPrice() != null ? a.getFinalPrice() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal avgCheck = completed > 0
                    ? revenue.divide(BigDecimal.valueOf(completed), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            BigDecimal completionRate = total > 0
                    ? BigDecimal.valueOf(completed).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            result.add(ServicePopularityDto.builder()
                    .serviceId(service.getId())
                    .name(service.getTitle())
                    .totalAppointments(total)
                    .completedAppointments(completed)
                    .cancelledAppointments(cancelled)
                    .revenue(revenue)
                    .avgCheck(avgCheck)
                    .completionRate(completionRate)
                    .costPrice(service.getCostPrice())
                    .build());
        }
        result.sort(Comparator.comparingInt(ServicePopularityDto::getTotalAppointments).reversed());
        return result;
    }

    @Transactional(readOnly = true)
    public ClientAnalyticsDto getClientAnalytics(Instant from, Instant to, String groupBy) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        List<Appointment> appointments = appointmentRepository.findByTenantIdAndDateRange(tenantId, from, to);

        // For each client, find the earliest-ever appointment to determine if "new" in range
        // We approximate "new" as: client's first appointment (across all time) falls within this range.
        // We do this by finding each client's first appointment in our appointments list for the range,
        // then checking if there's no appointment before "from" in our data.
        // A simpler approach: use Client.totalVisits context isn't available here,
        // so we check against all appointments for the tenant in range.

        // Collect unique clients in range
        Map<UUID, List<Appointment>> byClient = appointments.stream()
                .filter(a -> a.getClient() != null)
                .collect(Collectors.groupingBy(a -> a.getClient().getId()));

        // To determine "new" vs "returning", we need to know if the client had ANY appointment before "from"
        // We use the appointments list for the period and count clients who appear in the range
        // New = client whose FIRST EVER appointment (from our DB query perspective) falls in this range
        // We approximate: query appointments before "from" to find which clients existed already
        List<Appointment> previousAppts = appointmentRepository.findByTenantIdAndDateRange(
                tenantId,
                Instant.EPOCH,
                from
        );
        Set<UUID> existingClientIds = previousAppts.stream()
                .filter(a -> a.getClient() != null)
                .map(a -> a.getClient().getId())
                .collect(Collectors.toSet());

        int newClients = (int) byClient.keySet().stream()
                .filter(id -> !existingClientIds.contains(id))
                .count();
        int returningClients = (int) byClient.keySet().stream()
                .filter(existingClientIds::contains)
                .count();
        int totalUnique = byClient.size();
        double repeatRate = totalUnique > 0 ? (double) returningClients / totalUnique * 100 : 0;

        // Build time series
        List<ClientAnalyticsDto.DataPoint> series = buildClientSeries(
                appointments, existingClientIds, from, to, groupBy);

        return ClientAnalyticsDto.builder()
                .totalUniqueClients(totalUnique)
                .newClients(newClients)
                .returningClients(returningClients)
                .repeatRate(Math.round(repeatRate * 10.0) / 10.0)
                .series(series)
                .build();
    }

    @Transactional(readOnly = true)
    public PnlDto getPnl(Instant from, Instant to) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        List<Appointment> appointments = appointmentRepository.findByTenantIdAndDateRange(tenantId, from, to);

        BigDecimal revenue = appointments.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.DONE)
                .map(a -> a.getFinalPrice() != null ? a.getFinalPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal costOfSales = appointments.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.DONE && a.getService() != null
                        && a.getService().getCostPrice() != null)
                .map(a -> a.getService().getCostPrice())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal grossProfit = revenue.subtract(costOfSales);
        double grossMargin = revenue.compareTo(BigDecimal.ZERO) > 0
                ? grossProfit.divide(revenue, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0;

        Map<UUID, List<Appointment>> byArtist = appointments.stream()
                .filter(a -> a.getArtist() != null)
                .collect(Collectors.groupingBy(a -> a.getArtist().getId()));

        List<PnlDto.StaffLine> staffBreakdown = new ArrayList<>();
        BigDecimal totalCommissions = BigDecimal.ZERO;

        for (Map.Entry<UUID, List<Appointment>> entry : byArtist.entrySet()) {
            List<Appointment> artistApps = entry.getValue();
            Staff artist = artistApps.get(0).getArtist();

            BigDecimal artistRevenue = artistApps.stream()
                    .filter(a -> a.getStatus() == AppointmentStatus.DONE)
                    .map(a -> a.getFinalPrice() != null ? a.getFinalPrice() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            com.inkflow.crm.domain.enums.SalaryType salaryType = artist.getSalaryType() != null
                    ? artist.getSalaryType()
                    : com.inkflow.crm.domain.enums.SalaryType.NONE;
            BigDecimal salaryRate = artist.getSalaryRate();
            BigDecimal commission = BigDecimal.ZERO;
            if (salaryType == com.inkflow.crm.domain.enums.SalaryType.PERCENT && salaryRate != null) {
                commission = artistRevenue.multiply(salaryRate)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            } else if (salaryType == com.inkflow.crm.domain.enums.SalaryType.FIXED && salaryRate != null) {
                commission = salaryRate;
            }
            totalCommissions = totalCommissions.add(commission);
            staffBreakdown.add(PnlDto.StaffLine.builder()
                    .name(artist.getFirstName() + " " + artist.getLastName())
                    .revenue(artistRevenue)
                    .commission(commission)
                    .salaryType(salaryType.getValue())
                    .salaryRate(salaryRate)
                    .build());
        }
        staffBreakdown.sort(Comparator.comparing(PnlDto.StaffLine::getRevenue).reversed());

        BigDecimal otherExpenses = transactionRepository
                .sumByTypeAndDateRange(tenantId, TransactionType.EXPENSE, from, to);
        if (otherExpenses == null) otherExpenses = BigDecimal.ZERO;

        Map<String, TransactionCategoryConfig> configByKey = categoryConfigRepository
                .findByTenantIdAndDeletedAtIsNullOrderByIsDefaultDescLabelAsc(tenantId)
                .stream()
                .collect(Collectors.toMap(TransactionCategoryConfig::getCategoryKey, c -> c, (a, b) -> a));

        List<Object[]> categoryTotals = transactionRepository.sumByCategoryAndDateRange(tenantId, from, to);
        List<PnlDto.CategoryLine> expenseBreakdown = new ArrayList<>();
        for (Object[] row : categoryTotals) {
            TransactionCategory cat = (TransactionCategory) row[0];
            BigDecimal amount = row[1] != null ? (BigDecimal) row[1] : BigDecimal.ZERO;
            String key = cat.getValue();
            TransactionCategoryConfig cfg = configByKey.get(key);
            String plType = cfg != null ? cfg.getPlType() : "NEUTRAL";
            if ("EXPENSE".equals(plType)) {
                expenseBreakdown.add(PnlDto.CategoryLine.builder()
                        .categoryKey(key)
                        .label(cfg != null ? cfg.getLabel() : key)
                        .color(cfg != null ? cfg.getColor() : null)
                        .amount(amount)
                        .build());
            }
        }
        expenseBreakdown.sort(Comparator.comparing(PnlDto.CategoryLine::getAmount).reversed());

        BigDecimal netProfit = grossProfit.subtract(totalCommissions).subtract(otherExpenses);
        double netMargin = revenue.compareTo(BigDecimal.ZERO) > 0
                ? netProfit.divide(revenue, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0;

        return PnlDto.builder()
                .revenue(revenue)
                .costOfSales(costOfSales)
                .grossProfit(grossProfit)
                .grossMargin(Math.round(grossMargin * 10.0) / 10.0)
                .staffCommissions(totalCommissions)
                .otherExpenses(otherExpenses)
                .netProfit(netProfit)
                .netMargin(Math.round(netMargin * 10.0) / 10.0)
                .expenseBreakdown(expenseBreakdown)
                .staffBreakdown(staffBreakdown)
                .build();
    }

    private List<ClientAnalyticsDto.DataPoint> buildClientSeries(
            List<Appointment> appointments, Set<UUID> existingClientIds,
            Instant from, Instant to, String groupBy) {

        ZoneId zone = ZoneId.of("Europe/Kyiv");
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter weekFmt = DateTimeFormatter.ofPattern("yyyy-'W'ww");
        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("yyyy-MM");

        LocalDate startDate = from.atZone(zone).toLocalDate();
        LocalDate endDate = to.atZone(zone).toLocalDate();

        Map<String, List<Appointment>> grouped = new LinkedHashMap<>();
        if ("week".equals(groupBy)) {
            LocalDate cursor = startDate.with(java.time.DayOfWeek.MONDAY);
            while (!cursor.isAfter(endDate)) {
                grouped.put(cursor.format(weekFmt), new ArrayList<>());
                cursor = cursor.plusWeeks(1);
            }
        } else if ("month".equals(groupBy)) {
            LocalDate cursor = startDate.withDayOfMonth(1);
            while (!cursor.isAfter(endDate)) {
                grouped.put(cursor.format(monthFmt), new ArrayList<>());
                cursor = cursor.plusMonths(1);
            }
        } else {
            long days = Math.min(ChronoUnit.DAYS.between(startDate, endDate) + 1, 90);
            for (long i = 0; i < days; i++) {
                grouped.put(startDate.plusDays(i).format(dayFmt), new ArrayList<>());
            }
        }

        for (Appointment a : appointments) {
            LocalDate d = a.getStartTime().atZone(zone).toLocalDate();
            String key;
            if ("week".equals(groupBy)) {
                key = d.with(java.time.DayOfWeek.MONDAY).format(weekFmt);
            } else if ("month".equals(groupBy)) {
                key = d.withDayOfMonth(1).format(monthFmt);
            } else {
                key = d.format(dayFmt);
            }
            grouped.computeIfPresent(key, (k, list) -> { list.add(a); return list; });
        }

        return grouped.entrySet().stream().map(entry -> {
            List<Appointment> bucket = entry.getValue();
            // Unique clients in this bucket
            Set<UUID> bucketClients = bucket.stream()
                    .filter(a -> a.getClient() != null)
                    .map(a -> a.getClient().getId())
                    .collect(Collectors.toSet());
            int bucketNew = (int) bucketClients.stream().filter(id -> !existingClientIds.contains(id)).count();
            int bucketReturning = (int) bucketClients.stream().filter(existingClientIds::contains).count();

            return ClientAnalyticsDto.DataPoint.builder()
                    .date(entry.getKey())
                    .newClients(bucketNew)
                    .returningClients(bucketReturning)
                    .total(bucketNew + bucketReturning)
                    .build();
        }).collect(Collectors.toList());
    }

    private List<AppointmentAnalyticsDto.DataPoint> buildSeries(
            List<Appointment> appointments, Instant from, Instant to, String groupBy) {

        ZoneId zone = ZoneId.of("Europe/Kyiv");
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter weekFmt = DateTimeFormatter.ofPattern("yyyy-'W'ww");
        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("yyyy-MM");

        // Group appointments by bucket
        Map<String, List<Appointment>> grouped = new LinkedHashMap<>();

        // Pre-populate all buckets in range so we have zeros
        LocalDate startDate = from.atZone(zone).toLocalDate();
        LocalDate endDate = to.atZone(zone).toLocalDate();

        if ("week".equals(groupBy)) {
            LocalDate cursor = startDate.with(java.time.DayOfWeek.MONDAY);
            while (!cursor.isAfter(endDate)) {
                grouped.put(cursor.format(weekFmt), new ArrayList<>());
                cursor = cursor.plusWeeks(1);
            }
        } else if ("month".equals(groupBy)) {
            LocalDate cursor = startDate.withDayOfMonth(1);
            while (!cursor.isAfter(endDate)) {
                grouped.put(cursor.format(monthFmt), new ArrayList<>());
                cursor = cursor.plusMonths(1);
            }
        } else {
            // day (default)
            long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
            // Limit to max 90 buckets for day view
            long buckets = Math.min(days, 90);
            for (long i = 0; i < buckets; i++) {
                grouped.put(startDate.plusDays(i).format(dayFmt), new ArrayList<>());
            }
        }

        // Fill buckets
        for (Appointment a : appointments) {
            LocalDate apptDate = a.getStartTime().atZone(zone).toLocalDate();
            String key;
            if ("week".equals(groupBy)) {
                key = apptDate.with(java.time.DayOfWeek.MONDAY).format(weekFmt);
            } else if ("month".equals(groupBy)) {
                key = apptDate.withDayOfMonth(1).format(monthFmt);
            } else {
                key = apptDate.format(dayFmt);
            }
            grouped.computeIfPresent(key, (k, list) -> { list.add(a); return list; });
        }

        return grouped.entrySet().stream().map(entry -> {
            List<Appointment> bucket = entry.getValue();
            int bucketTotal = bucket.size();
            int bucketCompleted = (int) bucket.stream().filter(a -> a.getStatus() == AppointmentStatus.DONE).count();
            int bucketCancelled = (int) bucket.stream().filter(a -> a.getStatus() == AppointmentStatus.CANCELLED).count();
            BigDecimal bucketRevenue = bucket.stream()
                    .filter(a -> a.getStatus() == AppointmentStatus.DONE)
                    .map(a -> a.getFinalPrice() != null ? a.getFinalPrice() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            return AppointmentAnalyticsDto.DataPoint.builder()
                    .date(entry.getKey())
                    .total(bucketTotal)
                    .completed(bucketCompleted)
                    .cancelled(bucketCancelled)
                    .revenue(bucketRevenue)
                    .build();
        }).collect(Collectors.toList());
    }
}
