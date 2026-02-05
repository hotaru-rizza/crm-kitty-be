package com.inkflow.crm.module.payment.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.module.payment.dto.*;
import com.inkflow.crm.module.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Process a payment for an appointment
     */
    @PostMapping("/process")
    public ResponseEntity<ApiResponse<PaymentDto>> processPayment(
            @Valid @RequestBody ProcessPaymentRequest request) {
        PaymentDto payment = paymentService.processPayment(request);
        return ResponseEntity.ok(ApiResponse.success(payment));
    }

    /**
     * Process a refund for a transaction
     */
    @PostMapping("/refund")
    public ResponseEntity<ApiResponse<PaymentDto>> processRefund(
            @Valid @RequestBody ProcessRefundRequest request) {
        PaymentDto refund = paymentService.processRefund(request);
        return ResponseEntity.ok(ApiResponse.success(refund));
    }

    /**
     * Get payment summary for an appointment
     */
    @GetMapping("/appointment/{appointmentId}/summary")
    public ResponseEntity<ApiResponse<AppointmentPaymentSummaryDto>> getPaymentSummary(
            @PathVariable UUID appointmentId) {
        AppointmentPaymentSummaryDto summary = paymentService.getAppointmentPaymentSummary(appointmentId);
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    /**
     * Get payment history for an appointment
     */
    @GetMapping("/appointment/{appointmentId}")
    public ResponseEntity<ApiResponse<List<PaymentDto>>> getAppointmentPayments(
            @PathVariable UUID appointmentId) {
        List<PaymentDto> payments = paymentService.getAppointmentPayments(appointmentId);
        return ResponseEntity.ok(ApiResponse.success(payments));
    }
}
