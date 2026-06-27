package com.inkflow.crm.module.analytics.support;

import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.SalaryType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommissionCalculatorTest {

    private final CommissionCalculator calculator = new CommissionCalculator();

    @Test
    void shouldCalculatePercentCommissionWhenSalaryTypePercent() {
        Staff artist = Staff.builder()
                .salaryType(SalaryType.PERCENT)
                .salaryRate(BigDecimal.valueOf(15))
                .build();

        BigDecimal commission = calculator.calculate(artist, BigDecimal.valueOf(1000));

        assertEquals(new BigDecimal("150.00"), commission);
    }

    @Test
    void shouldReturnFixedSalaryWhenSalaryTypeFixed() {
        Staff artist = Staff.builder()
                .salaryType(SalaryType.FIXED)
                .salaryRate(BigDecimal.valueOf(500))
                .build();

        BigDecimal commission = calculator.calculate(artist, BigDecimal.valueOf(10_000));

        assertEquals(new BigDecimal("500.00"), commission);
    }

    @Test
    void shouldReturnZeroWhenSalaryTypeNone() {
        Staff artist = Staff.builder()
                .salaryType(SalaryType.NONE)
                .salaryRate(BigDecimal.valueOf(50))
                .build();

        BigDecimal commission = calculator.calculate(artist, BigDecimal.valueOf(1000));

        assertEquals(BigDecimal.ZERO, commission);
    }

    @Test
    void shouldReturnZeroWhenPercentTypeButRateMissing() {
        Staff artist = Staff.builder()
                .salaryType(SalaryType.PERCENT)
                .salaryRate(null)
                .build();

        BigDecimal commission = calculator.calculate(artist, BigDecimal.valueOf(1000));

        assertEquals(BigDecimal.ZERO, commission);
    }

    @Test
    void shouldProrateFixedSalaryForPartialMonth() {
        Staff artist = Staff.builder()
                .salaryType(SalaryType.FIXED)
                .salaryRate(BigDecimal.valueOf(3000))
                .build();

        ZoneId zone = ZoneId.of("Europe/Kyiv");
        Instant from = Instant.parse("2026-06-01T00:00:00+03:00");
        Instant to = Instant.parse("2026-06-15T23:59:59+03:00");

        BigDecimal earnings = calculator.calculateForPeriod(
                artist,
                BigDecimal.ZERO,
                from,
                to,
                zone);

        assertEquals(new BigDecimal("1500.00"), earnings);
    }

    @Test
    void shouldProrateFixedSalaryAcrossMultipleMonths() {
        Staff artist = Staff.builder()
                .salaryType(SalaryType.FIXED)
                .salaryRate(BigDecimal.valueOf(3000))
                .build();

        ZoneId zone = ZoneId.of("Europe/Kyiv");
        Instant from = Instant.parse("2026-06-16T00:00:00+03:00");
        Instant to = Instant.parse("2026-07-15T23:59:59+03:00");

        BigDecimal earnings = calculator.calculateForPeriod(
                artist,
                BigDecimal.ZERO,
                from,
                to,
                zone);

        assertEquals(new BigDecimal("2951.61"), earnings);
    }

    @Test
    void shouldResolveSalaryTypeDefaultingToNoneWhenUnset() {
        Staff artist = Staff.builder().salaryType(null).build();

        assertEquals(SalaryType.NONE, calculator.resolveSalaryType(artist));
    }
}
