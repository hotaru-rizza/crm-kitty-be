package com.inkflow.crm.module.analytics.support;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class AppointmentMetricsCalculator {

    public int countTotal(List<Appointment> appointments) {
        return appointments.size();
    }

    public int countByStatus(List<Appointment> appointments, AppointmentStatus status) {
        return (int) appointments.stream()
                .filter(appointment -> appointment.getStatus() == status)
                .count();
    }

    public int countCompleted(List<Appointment> appointments) {
        return countByStatus(appointments, AppointmentStatus.COMPLETED);
    }

    public int countCancelled(List<Appointment> appointments) {
        return countByStatus(appointments, AppointmentStatus.CANCELLED);
    }

    public BigDecimal sumDoneRevenue(List<Appointment> appointments) {
        return appointments.stream()
                .filter(this::isDone)
                .map(this::resolveFinalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal sumCostOfSales(List<Appointment> appointments) {
        return appointments.stream()
                .filter(this::isDone)
                .filter(appointment -> appointment.getService() != null)
                .filter(appointment -> appointment.getService().getCostPrice() != null)
                .map(appointment -> appointment.getService().getCostPrice())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal calculateAvgCheck(BigDecimal revenue, int completedCount) {
        if (completedCount <= 0) {
            return BigDecimal.ZERO;
        }
        return revenue.divide(BigDecimal.valueOf(completedCount), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateCompletionRate(int completedCount, int totalCount) {
        if (totalCount <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(completedCount)
                .divide(BigDecimal.valueOf(totalCount), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }

    public double calculateRepeatRate(int returningClients, int totalUniqueClients) {
        if (totalUniqueClients <= 0) {
            return 0;
        }
        return Math.round((double) returningClients / totalUniqueClients * 1000.0) / 10.0;
    }

    public double calculateUtilizationRate(double bookedHours, double scheduledHours) {
        if (scheduledHours <= 0) {
            return 0;
        }
        return Math.min(bookedHours / scheduledHours * 100, 100);
    }

    public double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public BigDecimal toPercent(BigDecimal part, BigDecimal whole) {
        if (whole.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return part.divide(whole, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    public boolean hasService(Appointment appointment) {
        return appointment.getService() != null;
    }

    public boolean hasArtist(Appointment appointment) {
        return appointment.getArtist() != null;
    }

    public boolean hasClient(Appointment appointment) {
        return appointment.getClient() != null;
    }

    public boolean isDone(Appointment appointment) {
        return appointment.getStatus() == AppointmentStatus.COMPLETED;
    }

    public boolean isCancelled(Appointment appointment) {
        return appointment.getStatus() == AppointmentStatus.CANCELLED;
    }

    public boolean isActive(Appointment appointment) {
        return appointment.getStatus() != AppointmentStatus.CANCELLED;
    }

    private BigDecimal resolveFinalPrice(Appointment appointment) {
        return appointment.getFinalPrice() != null ? appointment.getFinalPrice() : BigDecimal.ZERO;
    }
}
