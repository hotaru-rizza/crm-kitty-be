package com.inkflow.crm.module.subscription.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.module.subscription.dto.SubscriptionCheckoutResponse;
import com.inkflow.crm.module.subscription.dto.SubscriptionDto;
import com.inkflow.crm.module.subscription.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@Slf4j
@RestController
@RequestMapping("/subscription")
@RequiredArgsConstructor
@Tag(name = "CRM · Subscription")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping
    public ResponseEntity<ApiResponse<SubscriptionDto>> getCurrent() {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.getCurrentSubscription()));
    }

    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<SubscriptionCheckoutResponse>> checkout() {
        SubscriptionCheckoutResponse checkout = subscriptionService.createCheckout();
        log.info("Subscription checkout created via API: pageUrl={}", checkout.getPageUrl());

        return ResponseEntity.ok(ApiResponse.success(checkout));
    }
}
