package com.inkflow.crm.module.subscription.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SubscriptionCheckoutResponse {
    private String invoiceId;
    private String pageUrl;
    private String plan;
}
