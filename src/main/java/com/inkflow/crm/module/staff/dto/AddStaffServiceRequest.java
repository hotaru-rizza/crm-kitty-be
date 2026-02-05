package com.inkflow.crm.module.staff.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddStaffServiceRequest {
    private BigDecimal customPrice; // Optional: staff-specific price
    private Integer customDuration; // Optional: staff-specific duration
}
