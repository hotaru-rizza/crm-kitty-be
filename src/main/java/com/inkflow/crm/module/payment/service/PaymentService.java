package com.inkflow.crm.module.payment.service;

import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.module.payment.dto.AppointmentPaymentSummaryDto;
import com.inkflow.crm.module.payment.dto.PaymentDto;
import com.inkflow.crm.module.payment.dto.ProcessPaymentRequest;
import com.inkflow.crm.module.payment.dto.ProcessRefundRequest;
import com.inkflow.crm.module.payment.support.AppointmentPaymentSummaryCalculator;
import com.inkflow.crm.module.appointment.support.AppointmentAccessGuard;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentProcessingService paymentProcessingService;
    private final RefundProcessingService refundProcessingService;
    private final AppointmentPaymentSummaryCalculator summaryCalculator;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentAccessGuard appointmentAccessGuard;

    @Transactional
    public PaymentDto processPayment(ProcessPaymentRequest request) {
        return paymentProcessingService.processPayment(request);
    }

    @Transactional
    public PaymentDto processRefund(ProcessRefundRequest request) {
        return refundProcessingService.processRefund(request);
    }

    @Transactional(readOnly = true)
    public AppointmentPaymentSummaryDto getAppointmentPaymentSummary(UUID appointmentId) {
        Appointment appointment = requireAppointment(appointmentId);
        appointmentAccessGuard.requireView(appointment);
        return summaryCalculator.calculate(appointment);
    }

    @Transactional(readOnly = true)
    public List<PaymentDto> getAppointmentPayments(UUID appointmentId) {
        Appointment appointment = requireAppointment(appointmentId);
        appointmentAccessGuard.requireView(appointment);
        return summaryCalculator.listPayments(appointmentId);
    }

    private Appointment requireAppointment(UUID appointmentId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return appointmentRepository.findByIdAndDeletedAtIsNull(appointmentId)
                .orElseThrow(() -> ResourceNotFoundException.appointment(appointmentId.toString()));
    }
}
