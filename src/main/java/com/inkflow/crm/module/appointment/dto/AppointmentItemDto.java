package com.inkflow.crm.module.appointment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentItemDto {
    private UUID id;
    private String source;
    private UUID serviceId;
    private String serviceTitle;
    private String title;
    private Integer quantity;
    private BigDecimal unitPrice;
    private Integer durationMinutes;
    private BigDecimal lineTotal;
    private Integer sortOrder;
    private String pricingType;
}
