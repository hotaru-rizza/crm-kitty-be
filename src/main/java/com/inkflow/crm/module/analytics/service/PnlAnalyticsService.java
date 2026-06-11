package com.inkflow.crm.module.analytics.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.entity.TransactionCategoryConfig;
import com.inkflow.crm.domain.enums.TransactionCategory;
import com.inkflow.crm.domain.enums.TransactionType;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.TransactionCategoryConfigRepository;
import com.inkflow.crm.domain.repository.TransactionRepository;
import com.inkflow.crm.module.analytics.dto.PnlDto;
import com.inkflow.crm.module.analytics.support.AppointmentMetricsCalculator;
import com.inkflow.crm.module.analytics.support.CommissionCalculator;
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
public class PnlAnalyticsService {

    private final AppointmentRepository appointmentRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionCategoryConfigRepository categoryConfigRepository;
    private final CommissionCalculator commissionCalculator;
    private final AppointmentMetricsCalculator metrics;

    @Transactional(readOnly = true)
    public PnlDto getPnl(Instant from, Instant to) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        List<Appointment> appointments = appointmentRepository.findByTenantIdAndDateRange(tenantId, from, to);

        BigDecimal revenue = metrics.sumDoneRevenue(appointments);
        BigDecimal costOfSales = metrics.sumCostOfSales(appointments);
        BigDecimal grossProfit = revenue.subtract(costOfSales);
        double grossMargin = metrics.roundOneDecimal(metrics.toPercent(grossProfit, revenue).doubleValue());

        List<PnlDto.StaffLine> staffBreakdown = buildStaffBreakdown(appointments);
        BigDecimal totalCommissions = sumCommissions(staffBreakdown);
        BigDecimal otherExpenses = loadOtherExpenses(tenantId, from, to);
        List<PnlDto.CategoryLine> expenseBreakdown = buildExpenseBreakdown(tenantId, from, to);

        BigDecimal netProfit = grossProfit.subtract(totalCommissions).subtract(otherExpenses);
        double netMargin = metrics.roundOneDecimal(metrics.toPercent(netProfit, revenue).doubleValue());

        return PnlDto.builder()
                .revenue(revenue)
                .costOfSales(costOfSales)
                .grossProfit(grossProfit)
                .grossMargin(grossMargin)
                .staffCommissions(totalCommissions)
                .otherExpenses(otherExpenses)
                .netProfit(netProfit)
                .netMargin(netMargin)
                .expenseBreakdown(expenseBreakdown)
                .staffBreakdown(staffBreakdown)
                .build();
    }

    private BigDecimal sumCommissions(List<PnlDto.StaffLine> staffBreakdown) {
        return staffBreakdown.stream()
                .map(PnlDto.StaffLine::getCommission)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal loadOtherExpenses(UUID tenantId, Instant from, Instant to) {
        BigDecimal otherExpenses = transactionRepository
                .sumByTypeAndDateRange(tenantId, TransactionType.EXPENSE, from, to);
        return otherExpenses != null ? otherExpenses : BigDecimal.ZERO;
    }

    private List<PnlDto.StaffLine> buildStaffBreakdown(List<Appointment> appointments) {
        Map<UUID, List<Appointment>> appointmentsByArtist = groupAppointmentsByArtist(appointments);
        List<PnlDto.StaffLine> staffBreakdown = new ArrayList<>();

        for (List<Appointment> artistAppointments : appointmentsByArtist.values()) {
            staffBreakdown.add(toStaffLine(artistAppointments));
        }

        staffBreakdown.sort(Comparator.comparing(PnlDto.StaffLine::getRevenue).reversed());
        return staffBreakdown;
    }

    private Map<UUID, List<Appointment>> groupAppointmentsByArtist(List<Appointment> appointments) {
        return appointments.stream()
                .filter(metrics::hasArtist)
                .collect(Collectors.groupingBy(appointment -> appointment.getArtist().getId()));
    }

    private PnlDto.StaffLine toStaffLine(List<Appointment> artistAppointments) {
        Staff artist = artistAppointments.getFirst().getArtist();
        BigDecimal artistRevenue = metrics.sumDoneRevenue(artistAppointments);
        var salaryType = commissionCalculator.resolveSalaryType(artist);
        BigDecimal commission = commissionCalculator.calculate(artist, artistRevenue);

        return PnlDto.StaffLine.builder()
                .name(formatStaffName(artist))
                .revenue(artistRevenue)
                .commission(commission)
                .salaryType(salaryType.getValue())
                .salaryRate(artist.getSalaryRate())
                .build();
    }

    private List<PnlDto.CategoryLine> buildExpenseBreakdown(UUID tenantId, Instant from, Instant to) {
        Map<String, TransactionCategoryConfig> configByKey = loadCategoryConfigIndex(tenantId);
        List<Object[]> categoryTotals = transactionRepository.sumByCategoryAndDateRange(tenantId, from, to);
        List<PnlDto.CategoryLine> expenseBreakdown = new ArrayList<>();

        for (Object[] row : categoryTotals) {
            PnlDto.CategoryLine line = toCategoryLine(row, configByKey);
            if (line != null) {
                expenseBreakdown.add(line);
            }
        }

        expenseBreakdown.sort(Comparator.comparing(PnlDto.CategoryLine::getAmount).reversed());
        return expenseBreakdown;
    }

    private Map<String, TransactionCategoryConfig> loadCategoryConfigIndex(UUID tenantId) {
        return categoryConfigRepository
                .findByTenantIdAndDeletedAtIsNullOrderByIsDefaultDescLabelAsc(tenantId)
                .stream()
                .collect(Collectors.toMap(
                        TransactionCategoryConfig::getCategoryKey,
                        config -> config,
                        (left, right) -> left
                ));
    }

    private PnlDto.CategoryLine toCategoryLine(Object[] row, Map<String, TransactionCategoryConfig> configByKey) {
        TransactionCategory category = (TransactionCategory) row[0];
        BigDecimal amount = row[1] != null ? (BigDecimal) row[1] : BigDecimal.ZERO;
        String categoryKey = category.getValue();
        TransactionCategoryConfig config = configByKey.get(categoryKey);
        String plType = config != null ? config.getPlType() : "NEUTRAL";

        if (!"EXPENSE".equals(plType)) {
            return null;
        }

        return PnlDto.CategoryLine.builder()
                .categoryKey(categoryKey)
                .label(config != null ? config.getLabel() : categoryKey)
                .color(config != null ? config.getColor() : null)
                .amount(amount)
                .build();
    }

    private String formatStaffName(Staff artist) {
        return artist.getFirstName() + " " + artist.getLastName();
    }
}
