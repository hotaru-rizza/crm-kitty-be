package com.inkflow.crm.module.analytics.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.entity.StaffSchedule;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.StaffScheduleRepository;
import com.inkflow.crm.module.analytics.dto.StaffPerformanceDto;
import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.module.analytics.support.AnalyticsAppointmentScope;
import com.inkflow.crm.module.analytics.support.AppointmentMetricsCalculator;
import com.inkflow.crm.module.analytics.support.CommissionCalculator;
import com.inkflow.crm.module.analytics.support.StaffUtilizationCalculator;
import com.inkflow.crm.security.LocationScope;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StaffPerformanceAnalyticsService {

    private final InkflowProperties inkflowProperties;
    private final AppointmentRepository appointmentRepository;
    private final StaffScheduleRepository staffScheduleRepository;
    private final StaffUtilizationCalculator utilizationCalculator;
    private final CommissionCalculator commissionCalculator;
    private final AppointmentMetricsCalculator metrics;
    private final AnalyticsAppointmentScope appointmentScope;

    @Transactional(readOnly = true)
    public List<StaffPerformanceDto> getStaffPerformance(Instant from, Instant to) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID locationId = LocationScope.resolveFilter(null).orElse(null);
        List<Appointment> appointments = appointmentScope.findForCalendarAnalytics(from, to, locationId);

        Map<UUID, List<Appointment>> appointmentsByArtist = groupAppointmentsByArtist(appointments);
        Map<UUID, List<StaffSchedule>> schedulesByArtist = loadSchedulesByArtist(appointmentsByArtist.keySet());

        return appointmentsByArtist.values().stream()
                .map(artistAppointments -> toPerformanceDto(artistAppointments, schedulesByArtist, from, to))
                .sorted(Comparator.comparing(StaffPerformanceDto::getRevenue).reversed())
                .toList();
    }

    private Map<UUID, List<Appointment>> groupAppointmentsByArtist(List<Appointment> appointments) {
        return appointments.stream()
                .filter(metrics::hasArtist)
                .collect(Collectors.groupingBy(appointment -> appointment.getArtist().getId()));
    }

    private Map<UUID, List<StaffSchedule>> loadSchedulesByArtist(Iterable<UUID> artistIds) {
        List<UUID> artistIdList = new ArrayList<>();
        artistIds.forEach(artistIdList::add);

        return staffScheduleRepository.findByStaffIdIn(artistIdList).stream()
                .collect(Collectors.groupingBy(schedule -> schedule.getStaff().getId()));
    }

    private StaffPerformanceDto toPerformanceDto(
            List<Appointment> artistAppointments,
            Map<UUID, List<StaffSchedule>> schedulesByArtist,
            Instant from,
            Instant to) {
        Staff artist = artistAppointments.getFirst().getArtist();

        int total = metrics.countTotal(artistAppointments);
        int completed = metrics.countCompleted(artistAppointments);
        int cancelled = metrics.countCancelled(artistAppointments);
        int noShow = metrics.countNoShow(artistAppointments);
        BigDecimal revenue = metrics.sumDoneRevenue(artistAppointments);
        BigDecimal avgCheck = metrics.calculateAvgCheck(revenue, completed);

        var salaryType = commissionCalculator.resolveSalaryType(artist);
        BigDecimal calculatedSalary = commissionCalculator.calculateForPeriod(
                artist,
                revenue,
                from,
                to,
                inkflowProperties.defaultZoneId());

        List<StaffSchedule> artistSchedule = schedulesByArtist.getOrDefault(artist.getId(), List.of());
        double scheduledHours = utilizationCalculator.calculateScheduledHours(artistSchedule, from, to);
        double bookedHours = utilizationCalculator.calculateBookedHours(artistAppointments);
        double utilizationRate = metrics.calculateUtilizationRate(bookedHours, scheduledHours);

        return StaffPerformanceDto.builder()
                .staffId(artist.getId())
                .name(formatStaffName(artist))
                .avatar(artist.getAvatar())
                .calendarColor(artist.getCalendarColor())
                .totalAppointments(total)
                .completedAppointments(completed)
                .cancelledAppointments(cancelled)
                .noShowAppointments(noShow)
                .revenue(revenue)
                .avgCheck(avgCheck)
                .salaryType(salaryType.getValue())
                .salaryRate(artist.getSalaryRate())
                .calculatedSalary(calculatedSalary)
                .scheduledHours(metrics.roundOneDecimal(scheduledHours))
                .bookedHours(metrics.roundOneDecimal(bookedHours))
                .utilizationRate(metrics.roundOneDecimal(utilizationRate))
                .build();
    }

    private String formatStaffName(Staff artist) {
        return artist.getFirstName() + " " + artist.getLastName();
    }
}
