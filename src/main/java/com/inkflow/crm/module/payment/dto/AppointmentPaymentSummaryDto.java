package com.inkflow.crm.module.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentPaymentSummaryDto {
    private UUID appointmentId;
    
    // Price breakdown
    private BigDecimal servicePrice;
    private BigDecimal discount;
    private BigDecimal finalPrice;
    
    // Payment status
    private BigDecimal totalPaid;
    private BigDecimal depositPaid;
    private BigDecimal servicePaid;
    private BigDecimal totalRefunded;
    private BigDecimal totalTips;
    private BigDecimal remainingBalance;
    
    // Status flags
    private Boolean isFullyPaid;
    private Boolean hasDeposit;
    private Boolean hasRefunds;
    
    // Payment history
    private List<PaymentDto> payments;
    
    // Helper for display
    public String getPaymentStatus() {
        if (isFullyPaid) return "paid";
        if (totalPaid.compareTo(BigDecimal.ZERO) > 0) return "partial";
        return "unpaid";
    }
}
