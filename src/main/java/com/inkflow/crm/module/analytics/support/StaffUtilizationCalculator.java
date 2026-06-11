package com.inkflow.crm.module.analytics.support;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.StaffSchedule;
import com.inkflow.crm.domain.enums.DayOfWeek;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class StaffUtilizationCalculator {

    private static final ZoneId ANALYTICS_ZONE = ZoneId.of("Europe/Kyiv");

    private final AppointmentMetricsCalculator metrics;

    public double calculateScheduledHours(List<StaffSchedule> schedule, Instant from, Instant to) {
        if (schedule.isEmpty()) {
            return 0;
        }

        Map<DayOfWeek, StaffSchedule> scheduleByDay = indexWorkingDays(schedule);
        LocalDate startDate = from.atZone(ANALYTICS_ZONE).toLocalDate();
        LocalDate endDate = to.atZone(ANALYTICS_ZONE).toLocalDate();

        double totalHours = 0;
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            totalHours += resolveHoursForDate(cursor, scheduleByDay);
            cursor = cursor.plusDays(1);
        }

        return totalHours;
    }

    public double calculateBookedHours(List<Appointment> appointments) {
        return appointments.stream()
                .filter(metrics::isActive)
                .mapToLong(this::resolveDurationMinutes)
                .sum() / 60.0;
    }

    private Map<DayOfWeek, StaffSchedule> indexWorkingDays(List<StaffSchedule> schedule) {
        return schedule.stream()
                .filter(this::isWorkingDay)
                .collect(Collectors.toMap(StaffSchedule::getDayOfWeek, daySchedule -> daySchedule, (left, right) -> left));
    }

    private boolean isWorkingDay(StaffSchedule daySchedule) {
        return Boolean.TRUE.equals(daySchedule.getIsWorking())
                && daySchedule.getStartTime() != null
                && daySchedule.getEndTime() != null;
    }

    private double resolveHoursForDate(LocalDate date, Map<DayOfWeek, StaffSchedule> scheduleByDay) {
        DayOfWeek dayOfWeek = DayOfWeek.fromJavaDayOfWeek(date.getDayOfWeek());
        StaffSchedule daySchedule = scheduleByDay.get(dayOfWeek);

        if (daySchedule == null) {
            return 0;
        }

        return java.time.Duration.between(daySchedule.getStartTime(), daySchedule.getEndTime()).toMinutes() / 60.0;
    }

    private long resolveDurationMinutes(Appointment appointment) {
        return java.time.Duration.between(appointment.getStartTime(), appointment.getEndTime()).toMinutes();
    }
}
