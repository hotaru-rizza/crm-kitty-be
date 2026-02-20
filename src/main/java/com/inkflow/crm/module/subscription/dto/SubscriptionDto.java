package com.inkflow.crm.module.subscription.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class SubscriptionDto {
    private UUID id;
    private String plan;
    private String status;
    private boolean active;
    private long daysRemaining;
    private Instant trialEndsAt;
    private Instant currentPeriodEnd;
    private BigDecimal monthlyPrice;
}
