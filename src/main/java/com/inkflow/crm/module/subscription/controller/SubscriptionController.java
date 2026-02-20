package com.inkflow.crm.module.subscription.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.module.subscription.dto.SubscriptionCheckoutResponse;
import com.inkflow.crm.module.subscription.dto.SubscriptionDto;
import com.inkflow.crm.module.subscription.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping
    public ResponseEntity<ApiResponse<SubscriptionDto>> getCurrent() {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.getCurrentSubscription()));
    }

    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<SubscriptionCheckoutResponse>> checkout() {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.createCheckout()));
    }
}
