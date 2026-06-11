package com.inkflow.crm.module.analytics.support;

import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.SalaryType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class CommissionCalculator {

    public BigDecimal calculate(Staff artist, BigDecimal revenue) {
        SalaryType salaryType = artist.getSalaryType() != null ? artist.getSalaryType() : SalaryType.NONE;
        BigDecimal salaryRate = artist.getSalaryRate();

        if (salaryType == SalaryType.PERCENT && salaryRate != null) {
            return revenue.multiply(salaryRate)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        if (salaryType == SalaryType.FIXED && salaryRate != null) {
            return salaryRate;
        }
        return BigDecimal.ZERO;
    }

    public SalaryType resolveSalaryType(Staff artist) {
        return artist.getSalaryType() != null ? artist.getSalaryType() : SalaryType.NONE;
    }
}
