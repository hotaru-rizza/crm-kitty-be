package com.inkflow.crm.module.monobank.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.module.monobank.dto.*;
import com.inkflow.crm.module.monobank.service.MonobankService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/payments/monobank")
@RequiredArgsConstructor
public class MonobankController {

    private final MonobankService monobankService;

    /**
     * Creates a Monobank payment invoice and returns the payment page URL.
     * Called by CRM staff to generate an online payment link for a client.
     */
    @PostMapping("/invoice")
    public ResponseEntity<ApiResponse<OnlineInvoiceDto>> createInvoice(
            @Valid @RequestBody CreateOnlineInvoiceRequest request) {
        OnlineInvoiceDto invoice = monobankService.createInvoice(request);
        return ResponseEntity.ok(ApiResponse.success(invoice));
    }

    /**
     * Monobank webhook — receives payment status updates.
     * This endpoint must be publicly accessible (no JWT auth).
     * Monobank sends a POST request when invoice status changes.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody MonobankWebhookPayload payload) {
        log.info("Monobank webhook: invoiceId={} status={}", payload.getInvoiceId(), payload.getStatus());
        monobankService.handleWebhook(payload);
        return ResponseEntity.ok().build();
    }
}
