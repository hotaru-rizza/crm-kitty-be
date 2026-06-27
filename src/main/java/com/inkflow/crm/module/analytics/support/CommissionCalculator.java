package com.inkflow.crm.module.analytics.support;

import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.SalaryType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Component
public class CommissionCalculator {

    public BigDecimal calculate(Staff artist, BigDecimal revenue) {
        return calculateForPeriod(artist, revenue, null, null, ZoneId.systemDefault());
    }

    public BigDecimal calculateForPeriod(
            Staff artist,
            BigDecimal revenue,
            Instant from,
            Instant to,
            ZoneId zoneId) {
        SalaryType salaryType = resolveSalaryType(artist);
        BigDecimal salaryRate = artist.getSalaryRate();

        if (salaryType == SalaryType.PERCENT && salaryRate != null) {
            return revenue.multiply(salaryRate)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        if (salaryType == SalaryType.FIXED && salaryRate != null) {
            if (from == null || to == null) {
                return salaryRate.setScale(2, RoundingMode.HALF_UP);
            }
            return prorateFixedSalary(salaryRate, from, to, zoneId);
        }
        return BigDecimal.ZERO;
    }

    public SalaryType resolveSalaryType(Staff artist) {
        return artist.getSalaryType() != null ? artist.getSalaryType() : SalaryType.NONE;
    }

    private BigDecimal prorateFixedSalary(BigDecimal monthlyRate, Instant from, Instant to, ZoneId zoneId) {
        LocalDate rangeStart = LocalDate.ofInstant(from, zoneId);
        LocalDate rangeEnd = LocalDate.ofInstant(to, zoneId);

        if (rangeEnd.isBefore(rangeStart)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal total = BigDecimal.ZERO;
        LocalDate cursor = rangeStart;

        while (!cursor.isAfter(rangeEnd)) {
            YearMonth month = YearMonth.from(cursor);
            LocalDate monthEnd = month.atEndOfMonth();
            LocalDate segmentEnd = rangeEnd.isBefore(monthEnd) ? rangeEnd : monthEnd;
            long daysInSegment = ChronoUnit.DAYS.between(cursor, segmentEnd) + 1;
            int daysInMonth = month.lengthOfMonth();

            total = total.add(monthlyRate
                    .multiply(BigDecimal.valueOf(daysInSegment))
                    .divide(BigDecimal.valueOf(daysInMonth), 6, RoundingMode.HALF_UP));

            cursor = segmentEnd.plusDays(1);
        }

        return total.setScale(2, RoundingMode.HALF_UP);
    }
}
