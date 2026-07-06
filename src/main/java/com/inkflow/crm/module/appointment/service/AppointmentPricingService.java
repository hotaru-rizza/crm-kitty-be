package com.inkflow.crm.module.appointment.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.AppointmentItem;
import com.inkflow.crm.domain.entity.Service;
import com.inkflow.crm.domain.enums.AppointmentItemSource;
import com.inkflow.crm.domain.enums.PricingType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class AppointmentPricingService {

    public BigDecimal suggestUnitPrice(Service service, int durationMinutes) {
        if (service == null) {
            return BigDecimal.ZERO;
        }

        PricingType pricingType = service.getPricingType() != null
                ? service.getPricingType()
                : PricingType.FIXED;

        return switch (pricingType) {
            case HOURLY -> calculateHourlyPrice(service.getPrice(), durationMinutes);
            case PROJECT -> BigDecimal.ZERO;
            case FIXED -> service.getPrice() != null ? service.getPrice() : BigDecimal.ZERO;
        };
    }

    public void recompute(Appointment appointment, boolean adjustEndTime) {
        List<AppointmentItem> items = appointment.getItems();
        if (items == null || items.isEmpty()) {
            return;
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        int totalDurationMinutes = 0;

        for (AppointmentItem item : items) {
            item.recalculateLineTotal();
            subtotal = subtotal.add(item.getLineTotal());
            int qty = item.getQuantity() != null && item.getQuantity() > 0 ? item.getQuantity() : 1;
            totalDurationMinutes += (item.getDurationMinutes() != null ? item.getDurationMinutes() : 0) * qty;
        }

        BigDecimal discount = appointment.getDiscount() != null ? appointment.getDiscount() : BigDecimal.ZERO;
        appointment.setPrice(subtotal);
        appointment.setFinalPrice(subtotal.subtract(discount));
        syncPrimaryService(appointment);

        if (adjustEndTime && appointment.getStartTime() != null && totalDurationMinutes > 0) {
            appointment.setEndTime(appointment.getStartTime().plus(totalDurationMinutes, ChronoUnit.MINUTES));
        }
    }

    public int totalDurationMinutes(Appointment appointment) {
        if (appointment.getItems() == null) {
            return 0;
        }
        return appointment.getItems().stream()
                .mapToInt(item -> {
                    int qty = item.getQuantity() != null && item.getQuantity() > 0 ? item.getQuantity() : 1;
                    int duration = item.getDurationMinutes() != null ? item.getDurationMinutes() : 0;
                    return duration * qty;
                })
                .sum();
    }

    private void syncPrimaryService(Appointment appointment) {
        Optional<AppointmentItem> primaryItem = appointment.getItems().stream()
                .filter(item -> item.getSource() == AppointmentItemSource.SERVICE && item.getService() != null)
                .min(Comparator.comparingInt(AppointmentItem::getSortOrder));

        if (primaryItem.isPresent()) {
            appointment.setService(primaryItem.get().getService());
            return;
        }
        appointment.setService(null);
    }

    private BigDecimal calculateHourlyPrice(BigDecimal hourlyRate, int durationMinutes) {
        if (hourlyRate == null || durationMinutes <= 0) {
            return hourlyRate != null ? hourlyRate : BigDecimal.ZERO;
        }
        return hourlyRate
                .multiply(BigDecimal.valueOf(durationMinutes))
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }
}
