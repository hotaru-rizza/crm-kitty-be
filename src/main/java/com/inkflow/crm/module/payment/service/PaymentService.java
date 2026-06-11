package com.inkflow.crm.module.payment.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.*;
import com.inkflow.crm.domain.enums.*;
import com.inkflow.crm.domain.repository.*;
import com.inkflow.crm.module.payment.dto.*;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final TransactionRepository transactionRepository;
    private final AppointmentRepository appointmentRepository;
    private final StaffRepository staffRepository;

    private static final AtomicLong receiptCounter = new AtomicLong(System.currentTimeMillis() % 100000);

    @Transactional
    public PaymentDto processPayment(ProcessPaymentRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        Appointment appointment = appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(
                request.getAppointmentId(), tenantId)
                .orElseThrow(() -> ResourceNotFoundException.appointment(request.getAppointmentId().toString()));

        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new BusinessRuleException("Cannot process payment for cancelled appointment");
        }

        Staff processedBy = staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(currentUserId, tenantId)
                .orElse(null);

        PaymentType paymentType = request.getPaymentType() != null
                ? PaymentType.fromValue(request.getPaymentType())
                : PaymentType.SERVICE_PAYMENT;

        AppointmentPaymentSummaryDto summary = getPaymentSummary(appointment);
        validatePaymentAmount(request.getAmount(), summary, paymentType);

        PaymentMethod paymentMethod = PaymentMethod.fromValue(request.getPaymentMethod());
        if (paymentMethod == PaymentMethod.SPLIT) {
            validateSplitPayment(request);
        }

        Transaction transaction = Transaction.builder()
                .tenantId(tenantId)
                .type(TransactionType.INCOME)
                .category(TransactionCategory.SERVICE)
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
                .receiptNumber(generateReceiptNumber(tenantId))
                .isRefunded(false)
                .refundedAmount(BigDecimal.ZERO)
                .build();

        transaction = transactionRepository.save(transaction);
        log.info("Payment processed: tenantId={} transactionId={} appointmentId={}",
                tenantId, transaction.getId(), appointment.getId());

        if (request.getTipAmount() != null && request.getTipAmount().compareTo(BigDecimal.ZERO) > 0) {
            createTipTransaction(appointment, request.getTipAmount(), paymentMethod, processedBy, tenantId);
        }

        if (paymentType == PaymentType.DEPOSIT) {
            appointment.setPrepayment(appointment.getPrepayment().add(request.getAmount()));
            appointmentRepository.save(appointment);
        }

        return mapToPaymentDto(transaction);
    }

    @Transactional
    public PaymentDto processRefund(ProcessRefundRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        Transaction originalTransaction = transactionRepository.findByIdAndTenantIdAndDeletedAtIsNull(
                request.getTransactionId(), tenantId)
                .orElseThrow(() -> ResourceNotFoundException.transaction(request.getTransactionId().toString()));

        if (!originalTransaction.canBeRefunded()) {
            throw new BusinessRuleException("This transaction cannot be refunded");
        }

        if (request.getAmount().compareTo(originalTransaction.getRefundableAmount()) > 0) {
            throw new BusinessRuleException(
                    String.format("Refund amount (%.2f) exceeds refundable amount (%.2f)",
                            request.getAmount(), originalTransaction.getRefundableAmount()));
        }

        Staff processedBy = staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(currentUserId, tenantId)
                .orElse(null);

        Transaction refundTransaction = Transaction.builder()
                .tenantId(tenantId)
                .type(TransactionType.EXPENSE)
                .category(TransactionCategory.SERVICE)
                .paymentType(PaymentType.REFUND)
                .amount(request.getAmount())
                .paymentMethod(PaymentMethod.fromValue(request.getPaymentMethod()))
                .description(String.format("Повернення: %s", request.getReason()))
                .appointment(originalTransaction.getAppointment())
                .staff(originalTransaction.getStaff())
                .processedBy(processedBy)
                .location(originalTransaction.getLocation())
                .date(Instant.now())
                .originalTransactionId(originalTransaction.getId())
                .refundReason(request.getReason())
                .receiptNumber(generateReceiptNumber(tenantId))
                .isRefunded(false)
                .refundedAmount(BigDecimal.ZERO)
                .build();

        refundTransaction = transactionRepository.save(refundTransaction);

        originalTransaction.addRefundedAmount(request.getAmount());
        transactionRepository.save(originalTransaction);

        log.info("Refund processed: tenantId={} refundId={} originalTransactionId={}",
                tenantId, refundTransaction.getId(), originalTransaction.getId());

        if (originalTransaction.getPaymentType() == PaymentType.DEPOSIT) {
            Appointment appointment = originalTransaction.getAppointment();
            if (appointment != null) {
                BigDecimal newPrepayment = appointment.getPrepayment().subtract(request.getAmount());
                appointment.setPrepayment(newPrepayment.max(BigDecimal.ZERO));
                appointmentRepository.save(appointment);
            }
        }

        return mapToPaymentDto(refundTransaction);
    }

    @Transactional(readOnly = true)
    public AppointmentPaymentSummaryDto getAppointmentPaymentSummary(UUID appointmentId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Appointment appointment = appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(appointmentId, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.appointment(appointmentId.toString()));

        return getPaymentSummary(appointment);
    }

    @Transactional(readOnly = true)
    public List<PaymentDto> getAppointmentPayments(UUID appointmentId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(appointmentId, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.appointment(appointmentId.toString()));

        List<Transaction> transactions = transactionRepository.findByAppointmentIdAndDeletedAtIsNullOrderByDateDesc(appointmentId);

        return transactions.stream()
                .map(this::mapToPaymentDto)
                .toList();
    }

    private AppointmentPaymentSummaryDto getPaymentSummary(Appointment appointment) {
        List<Transaction> transactions = transactionRepository
                .findByAppointmentIdAndDeletedAtIsNullOrderByDateDesc(appointment.getId());

        BigDecimal totalPaid = BigDecimal.ZERO;
        BigDecimal depositPaid = BigDecimal.ZERO;
        BigDecimal servicePaid = BigDecimal.ZERO;
        BigDecimal totalRefunded = BigDecimal.ZERO;
        BigDecimal totalTips = BigDecimal.ZERO;

        for (Transaction tx : transactions) {
            if (tx.getPaymentType() == null) continue;

            switch (tx.getPaymentType()) {
                case DEPOSIT:
                    depositPaid = depositPaid.add(tx.getAmount());
                    totalPaid = totalPaid.add(tx.getAmount());
                    break;
                case SERVICE_PAYMENT:
                    servicePaid = servicePaid.add(tx.getAmount());
                    totalPaid = totalPaid.add(tx.getAmount());
                    break;
                case REFUND:
                    totalRefunded = totalRefunded.add(tx.getAmount());
                    totalPaid = totalPaid.subtract(tx.getAmount());
                    break;
                case TIP:
                    totalTips = totalTips.add(tx.getAmount());
                    break;
            }
        }

        BigDecimal finalPrice = appointment.getFinalPrice() != null
                ? appointment.getFinalPrice()
                : appointment.getPrice().subtract(appointment.getDiscount());

        BigDecimal remainingBalance = finalPrice.subtract(totalPaid);
        if (remainingBalance.compareTo(BigDecimal.ZERO) < 0) {
            remainingBalance = BigDecimal.ZERO;
        }

        List<PaymentDto> payments = transactions.stream()
                .map(this::mapToPaymentDto)
                .collect(Collectors.toList());

        return AppointmentPaymentSummaryDto.builder()
                .appointmentId(appointment.getId())
                .servicePrice(appointment.getPrice())
                .discount(appointment.getDiscount())
                .finalPrice(finalPrice)
                .totalPaid(totalPaid)
                .depositPaid(depositPaid)
                .servicePaid(servicePaid)
                .totalRefunded(totalRefunded)
                .totalTips(totalTips)
                .remainingBalance(remainingBalance)
                .isFullyPaid(remainingBalance.compareTo(BigDecimal.ZERO) <= 0)
                .hasDeposit(depositPaid.compareTo(BigDecimal.ZERO) > 0)
                .hasRefunds(totalRefunded.compareTo(BigDecimal.ZERO) > 0)
                .payments(payments)
                .build();
    }

    private void validatePaymentAmount(BigDecimal amount, AppointmentPaymentSummaryDto summary, PaymentType paymentType) {
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

    private void createTipTransaction(Appointment appointment, BigDecimal tipAmount,
            PaymentMethod paymentMethod, Staff processedBy, UUID tenantId) {
        Transaction tipTransaction = Transaction.builder()
                .tenantId(tenantId)
                .type(TransactionType.INCOME)
                .category(TransactionCategory.SERVICE)
                .paymentType(PaymentType.TIP)
                .amount(tipAmount)
                .paymentMethod(paymentMethod)
                .description("Чайові")
                .appointment(appointment)
                .staff(appointment.getArtist())
                .processedBy(processedBy)
                .location(appointment.getLocation())
                .date(Instant.now())
                .tipAmount(tipAmount)
                .receiptNumber(generateReceiptNumber(tenantId))
                .isRefunded(false)
                .refundedAmount(BigDecimal.ZERO)
                .build();

        transactionRepository.save(tipTransaction);
        log.info("Tip recorded: {} for appointment {}", tipAmount, appointment.getId());
    }

    private String buildPaymentDescription(ProcessPaymentRequest request,
            Appointment appointment, PaymentType paymentType) {
        String typeLabel = paymentType == PaymentType.DEPOSIT ? "Передоплата" : "Оплата послуги";
        String serviceName = appointment.getService() != null ? appointment.getService().getTitle() : "Послуга";

        if (request.getDescription() != null && !request.getDescription().isEmpty()) {
            return String.format("%s: %s - %s", typeLabel, serviceName, request.getDescription());
        }
        return String.format("%s: %s", typeLabel, serviceName);
    }

    private String generateReceiptNumber(UUID tenantId) {
        String datePrefix = LocalDateTime.now(ZoneId.of("Europe/Kiev"))
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long sequenceNum = receiptCounter.incrementAndGet() % 100000;
        return String.format("%s-%05d", datePrefix, sequenceNum);
    }

    private PaymentDto mapToPaymentDto(Transaction transaction) {
        return PaymentDto.builder()
                .id(transaction.getId())
                .paymentType(transaction.getPaymentType() != null ? transaction.getPaymentType().getValue() : null)
                .paymentTypeLabel(transaction.getPaymentType() != null ? transaction.getPaymentType().getDescription() : null)
                .amount(transaction.getAmount())
                .paymentMethod(transaction.getPaymentMethod().getValue())
                .paymentMethodLabel(transaction.getPaymentMethod().getDescription())
                .cashAmount(transaction.getCashAmount())
                .cardAmount(transaction.getCardAmount())
                .tipAmount(transaction.getTipAmount())
                .description(transaction.getDescription())
                .date(transaction.getDate())
                .receiptNumber(transaction.getReceiptNumber())
                .isRefunded(transaction.getIsRefunded())
                .refundedAmount(transaction.getRefundedAmount())
                .refundableAmount(transaction.getRefundableAmount())
                .originalTransactionId(transaction.getOriginalTransactionId())
                .refundReason(transaction.getRefundReason())
                .processedById(transaction.getProcessedBy() != null ? transaction.getProcessedBy().getId() : null)
                .processedByName(transaction.getProcessedBy() != null ? transaction.getProcessedBy().getFullName() : null)
                .clientName(transaction.getAppointment() != null && transaction.getAppointment().getClient() != null
                        ? transaction.getAppointment().getClient().getFirstName() + " " +
                          transaction.getAppointment().getClient().getLastName()
                        : null)
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
