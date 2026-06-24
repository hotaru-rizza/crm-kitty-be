package com.inkflow.crm.module.payment.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.entity.Transaction;
import com.inkflow.crm.domain.enums.*;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TransactionRepository;
import com.inkflow.crm.module.payment.dto.AppointmentPaymentSummaryDto;
import com.inkflow.crm.module.payment.dto.PaymentDto;
import com.inkflow.crm.module.payment.dto.ProcessPaymentRequest;
import com.inkflow.crm.module.payment.mapper.PaymentMapper;
import com.inkflow.crm.module.payment.support.AppointmentPaymentSummaryCalculator;
import com.inkflow.crm.module.payment.support.ReceiptNumberGenerator;
import com.inkflow.crm.module.project.service.ProjectProgressSyncService;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentProcessingService {

    private final TransactionRepository transactionRepository;
    private final AppointmentRepository appointmentRepository;
    private final StaffRepository staffRepository;
    private final PaymentMapper paymentMapper;
    private final AppointmentPaymentSummaryCalculator summaryCalculator;
    private final ReceiptNumberGenerator receiptNumberGenerator;
    private final ProjectProgressSyncService projectProgressSyncService;

    @Transactional
    public PaymentDto processPayment(ProcessPaymentRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        Appointment appointment = requireAppointment(request.getAppointmentId(), tenantId);
        rejectCancelledAppointment(appointment);

        Staff processedBy = findProcessedByStaff(currentUserId, tenantId);
        PaymentType paymentType = resolvePaymentType(request);
        AppointmentPaymentSummaryDto summary = summaryCalculator.calculate(appointment);

        validatePaymentAmount(request.getAmount(), summary, paymentType);

        PaymentMethod paymentMethod = PaymentMethod.fromValue(request.getPaymentMethod());
        if (paymentMethod == PaymentMethod.SPLIT) {
            validateSplitPayment(request);
        }

        Transaction transaction = buildPaymentTransaction(request, appointment, paymentType, paymentMethod, processedBy, tenantId);
        transaction = transactionRepository.save(transaction);

        log.info("Payment processed: tenantId={} transactionId={} appointmentId={}",
                tenantId, transaction.getId(), appointment.getId());

        recordTipIfPresent(request, appointment, paymentMethod, processedBy, tenantId);
        applyDepositIfNeeded(request, appointment, paymentType);
        syncLinkedProjectProgress(appointment);

        return paymentMapper.toDto(transaction);
    }

    private Appointment requireAppointment(UUID appointmentId, UUID tenantId) {
        return appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(appointmentId, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.appointment(appointmentId.toString()));
    }

    private void rejectCancelledAppointment(Appointment appointment) {
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new BusinessRuleException("Cannot process payment for cancelled appointment");
        }
    }

    private Staff findProcessedByStaff(UUID currentUserId, UUID tenantId) {
        return staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(currentUserId, tenantId)
                .orElse(null);
    }

    private PaymentType resolvePaymentType(ProcessPaymentRequest request) {
        if (request.getPaymentType() == null) {
            return PaymentType.SERVICE_PAYMENT;
        }
        return PaymentType.fromValue(request.getPaymentType());
    }

    private Transaction buildPaymentTransaction(
            ProcessPaymentRequest request,
            Appointment appointment,
            PaymentType paymentType,
            PaymentMethod paymentMethod,
            Staff processedBy,
            UUID tenantId) {
        return Transaction.builder()
                .tenantId(tenantId)
                .type(TransactionType.INCOME)
                .category(TransactionCategory.SERVICE.getValue())
                .paymentType(paymentType)
                .amount(request.getAmount())
                .paymentMethod(paymentMethod)
                .description(buildPaymentDescription(request, appointment, paymentType))
                .appointment(appointment)
                .staff(appointment.getArtist())
                .processedBy(processedBy)
                .location(appointment.getLocation())
                .date(Instant.now())
                .cashAmount(request.getCashAmount())
                .cardAmount(request.getCardAmount())
                .tipAmount(request.getTipAmount())
                .receiptNumber(receiptNumberGenerator.generate())
                .isRefunded(false)
                .refundedAmount(BigDecimal.ZERO)
                .build();
    }

    private void recordTipIfPresent(
            ProcessPaymentRequest request,
            Appointment appointment,
            PaymentMethod paymentMethod,
            Staff processedBy,
            UUID tenantId) {
        if (request.getTipAmount() == null || request.getTipAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        Transaction tipTransaction = Transaction.builder()
                .tenantId(tenantId)
                .type(TransactionType.INCOME)
                .category(TransactionCategory.TIP.getValue())
                .paymentType(PaymentType.TIP)
                .amount(request.getTipAmount())
                .paymentMethod(paymentMethod)
                .description("Чайові")
                .appointment(appointment)
                .staff(appointment.getArtist())
                .processedBy(processedBy)
                .location(appointment.getLocation())
                .date(Instant.now())
                .tipAmount(request.getTipAmount())
                .receiptNumber(receiptNumberGenerator.generate())
                .isRefunded(false)
                .refundedAmount(BigDecimal.ZERO)
                .build();

        transactionRepository.save(tipTransaction);
        log.info("Tip recorded: {} for appointment {}", request.getTipAmount(), appointment.getId());
    }

    private void applyDepositIfNeeded(ProcessPaymentRequest request, Appointment appointment, PaymentType paymentType) {
        if (paymentType != PaymentType.DEPOSIT) {
            return;
        }
        appointment.setPrepayment(appointment.getPrepayment().add(request.getAmount()));
        appointmentRepository.save(appointment);
    }

    private void validatePaymentAmount(
            BigDecimal amount,
            AppointmentPaymentSummaryDto summary,
            PaymentType paymentType) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Payment amount must be greater than 0");
        }

        if (paymentType == PaymentType.SERVICE_PAYMENT || paymentType == PaymentType.DEPOSIT) {
            BigDecimal remaining = summary.getRemainingBalance();
            if (amount.compareTo(remaining) > 0) {
                log.warn("Payment amount {} exceeds remaining balance {}", amount, remaining);
            }
        }
    }

    private void validateSplitPayment(ProcessPaymentRequest request) {
        if (request.getCashAmount() == null || request.getCardAmount() == null) {
            throw new BusinessRuleException("Split payment requires both cash and card amounts");
        }

        BigDecimal total = request.getCashAmount().add(request.getCardAmount());
        if (total.compareTo(request.getAmount()) != 0) {
            throw new BusinessRuleException(
                    String.format("Split amounts (%.2f + %.2f = %.2f) must equal total amount (%.2f)",
                            request.getCashAmount(), request.getCardAmount(), total, request.getAmount()));
        }
    }

    private String buildPaymentDescription(
            ProcessPaymentRequest request,
            Appointment appointment,
            PaymentType paymentType) {
        String typeLabel = paymentType == PaymentType.DEPOSIT ? "Передоплата" : "Оплата послуги";
        String serviceName = appointment.getService() != null ? appointment.getService().getTitle() : "Послуга";

        if (request.getDescription() != null && !request.getDescription().isEmpty()) {
            return String.format("%s: %s - %s", typeLabel, serviceName, request.getDescription());
        }
        return String.format("%s: %s", typeLabel, serviceName);
    }

    private void syncLinkedProjectProgress(Appointment appointment) {
        if (appointment.getProject() == null) {
            return;
        }
        projectProgressSyncService.syncProject(appointment.getProject().getId());
    }
}
