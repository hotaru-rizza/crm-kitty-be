package com.inkflow.crm.module.payment.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.module.payment.dto.*;
import com.inkflow.crm.module.payment.service.PaymentService;
import com.inkflow.crm.security.RequirePermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/process")
    @RequirePermission("payments.process")
    public ResponseEntity<ApiResponse<PaymentDto>> processPayment(
            @Valid @RequestBody ProcessPaymentRequest request) {
        PaymentDto payment = paymentService.processPayment(request);
        log.info("Payment processed via API: appointmentId={} amount={}", request.getAppointmentId(), request.getAmount());

        return ResponseEntity.ok(ApiResponse.success(payment));
    }

    @PostMapping("/refund")
    @RequirePermission("payments.process")
    public ResponseEntity<ApiResponse<PaymentDto>> processRefund(
            @Valid @RequestBody ProcessRefundRequest request) {
        PaymentDto payment = paymentService.processRefund(request);
        log.info("Refund processed via API: transactionId={} amount={}", request.getTransactionId(), request.getAmount());

        return ResponseEntity.ok(ApiResponse.success(payment));
    }

    @GetMapping("/appointment/{appointmentId}/summary")
    @RequirePermission({"payments.view", "finance.view"})
    public ResponseEntity<ApiResponse<AppointmentPaymentSummaryDto>> getPaymentSummary(
            @PathVariable UUID appointmentId) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getAppointmentPaymentSummary(appointmentId)));
    }

    @GetMapping("/appointment/{appointmentId}")
    @RequirePermission({"payments.view", "finance.view"})
    public ResponseEntity<ApiResponse<List<PaymentDto>>> getAppointmentPayments(
            @PathVariable UUID appointmentId) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getAppointmentPayments(appointmentId)));
    }
}
