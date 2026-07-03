package com.inkflow.crm.module.staff.service;

import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.module.analytics.support.AppointmentMetricsCalculator;
import com.inkflow.crm.module.analytics.support.CommissionCalculator;
import com.inkflow.crm.module.staff.dto.StaffEarningsDto;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StaffEarningsService {

    private final StaffLookup staffLookup;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentMetricsCalculator metricsCalculator;
    private final CommissionCalculator commissionCalculator;
    private final InkflowProperties inkflowProperties;

    @Transactional(readOnly = true)
    public StaffEarningsDto getEarnings(UUID staffId, Instant from, Instant to) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Staff staff = staffLookup.requireStaff(staffId);

        List<Appointment> appointments = appointmentRepository.findByArtistIdAndDateRange( staffId, from, to);

        BigDecimal revenue = metricsCalculator.sumDoneRevenue(appointments);
        int completedCount = metricsCalculator.countCompleted(appointments);
        var salaryType = commissionCalculator.resolveSalaryType(staff);
        BigDecimal earnings = commissionCalculator.calculateForPeriod(
                staff,
                revenue,
                from,
                to,
                inkflowProperties.defaultZoneId());

        return StaffEarningsDto.builder()
                .staffId(staffId)
                .revenue(revenue)
                .salaryType(salaryType.getValue())
                .rate(staff.getSalaryRate())
                .earnings(earnings)
                .appointmentsCount(completedCount)
                .build();
    }
}
