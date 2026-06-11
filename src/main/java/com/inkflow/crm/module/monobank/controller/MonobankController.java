package com.inkflow.crm.module.monobank.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.module.monobank.dto.*;
import com.inkflow.crm.module.monobank.service.MonobankService;
import com.inkflow.crm.security.RequirePermission;
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

    @PostMapping("/invoice")
    @RequirePermission("payments.process")
    public ResponseEntity<ApiResponse<OnlineInvoiceDto>> createInvoice(
            @Valid @RequestBody CreateOnlineInvoiceRequest request) {
        OnlineInvoiceDto invoice = monobankService.createInvoice(request);
        log.info("Monobank invoice created via API: appointmentId={}", request.getAppointmentId());

        return ResponseEntity.ok(ApiResponse.success(invoice));
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody MonobankWebhookPayload payload) {
        log.info("Monobank webhook: invoiceId={} status={}", payload.getInvoiceId(), payload.getStatus());
        monobankService.handleWebhook(payload);
        return ResponseEntity.ok().build();
    }
}
