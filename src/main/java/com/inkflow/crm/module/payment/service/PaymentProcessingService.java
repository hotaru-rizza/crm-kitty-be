package com.inkflow.crm.module.payment.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.entity.Transaction;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.enums.AuditAction;
import com.inkflow.crm.domain.enums.AuditEntityType;
import com.inkflow.crm.domain.enums.ClientBalanceReason;
import com.inkflow.crm.domain.enums.PaymentMethod;
import com.inkflow.crm.domain.enums.PaymentType;
import com.inkflow.crm.domain.enums.TransactionCategory;
import com.inkflow.crm.domain.enums.TransactionType;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.ClientBalanceEntryRepository;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TransactionRepository;
import com.inkflow.crm.module.appointment.support.AppointmentLabels;
import com.inkflow.crm.module.appointment.support.AppointmentAccessGuard;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.audit.support.AuditLabelFormatter;
import com.inkflow.crm.module.client.service.ClientBalanceService;
import com.inkflow.crm.module.payment.dto.AppointmentPaymentSummaryDto;
import com.inkflow.crm.module.payment.dto.PaymentDto;
import com.inkflow.crm.module.payment.dto.PaymentLineRequest;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentProcessingService {

    private final TransactionRepository transactionRepository;
    private final AppointmentRepository appointmentRepository;
    private final ClientRepository clientRepository;
    private final StaffRepository staffRepository;
    private final PaymentMapper paymentMapper;
    private final AppointmentPaymentSummaryCalculator summaryCalculator;
    private final ReceiptNumberGenerator receiptNumberGenerator;
    private final ProjectProgressSyncService projectProgressSyncService;
    private final AuditRecorder auditRecorder;
    private final AuditLabelFormatter auditLabelFormatter;
    private final ClientBalanceService clientBalanceService;
    private final ClientBalanceEntryRepository balanceEntryRepository;
    private final AppointmentAccessGuard appointmentAccessGuard;

    @Transactional
    public PaymentDto processPayment(ProcessPaymentRequest request) {
        ProcessPaymentRequest normalized = normalizeSplitPayments(request);

        if (hasLines(normalized)) {
            return processPaymentLines(normalized);
        }

        validateLegacyRequest(normalized);
        return processSinglePayment(normalized);
    }

    private PaymentDto processSinglePayment(ProcessPaymentRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        Appointment appointment = requireAppointment(request.getAppointmentId(), tenantId);
        appointmentAccessGuard.requireEdit(appointment);
        rejectCancelledAppointment(appointment);
        rejectReservationPayment(appointment);

        Staff processedBy = findProcessedByStaff(currentUserId, tenantId);
        PaymentType paymentType = resolvePaymentType(request);
        AppointmentPaymentSummaryDto summary = summaryCalculator.calculate(appointment);

        validatePaymentAmount(request.getAmount(), summary, paymentType);

        PaymentMethod paymentMethod = PaymentMethod.fromValue(request.getPaymentMethod());
        if (paymentMethod == PaymentMethod.BALANCE) {
            validateBalancePayment(appointment, request.getAmount(), paymentType);
        }

        Transaction transaction = buildPaymentTransaction(
                request.getAmount(),
                request.getCashAmount(),
                request.getCardAmount(),
                request.getTipAmount(),
                resolveCategoryForPaymentType(paymentType),
                request,
                appointment,
                paymentType,
                paymentMethod,
                processedBy,
                tenantId
        );
        transaction = transactionRepository.save(transaction);

        log.info("Payment processed: tenantId={} transactionId={} appointmentId={}",
                tenantId, transaction.getId(), appointment.getId());

        auditPayment(appointment, transaction);
        recordClientBalanceForPayment(
                appointment,
                transaction,
                paymentMethod,
                paymentType,
                summary.getRemainingBalance()
        );
        recordTipIfPresent(request, appointment, paymentMethod, processedBy, tenantId);
        applyDepositIfNeeded(request.getAmount(), appointment, paymentType);
        syncLinkedProjectProgress(appointment);

        return paymentMapper.toDto(transaction);
    }

    public void recordInitialPrepayment(Appointment appointment) {
        if (appointment.isReservation()) {
            return;
        }

        BigDecimal amount = appointment.getPrepayment() != null ? appointment.getPrepayment() : BigDecimal.ZERO;
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        UUID tenantId = appointment.getTenantId();
        Staff processedBy = findProcessedByStaff(SecurityUtils.getCurrentUserId(), tenantId);
        String serviceName = AppointmentLabels.serviceTitle(appointment);

        Transaction transaction = Transaction.builder()
                .tenantId(tenantId)
                .type(TransactionType.INCOME)
                .category(TransactionCategory.SERVICE.getValue())
                .paymentType(PaymentType.DEPOSIT)
                .amount(amount)
                .paymentMethod(PaymentMethod.CASH)
                .description(String.format("Передоплата: %s", serviceName))
                .appointment(appointment)
                .staff(appointment.getArtist())
                .processedBy(processedBy)
                .location(appointment.getLocation())
                .date(Instant.now())
                .receiptNumber(receiptNumberGenerator.generate())
                .isRefunded(false)
                .refundedAmount(BigDecimal.ZERO)
                .build();

        transaction = transactionRepository.save(transaction);

        log.info("Initial prepayment recorded: tenantId={} transactionId={} appointmentId={} amount={}",
                tenantId, transaction.getId(), appointment.getId(), amount);

        auditPayment(appointment, transaction);
        recordClientBalanceForPayment(
                appointment,
                transaction,
                PaymentMethod.CASH,
                PaymentType.DEPOSIT,
                appointment.getFinalPrice() != null ? appointment.getFinalPrice() : BigDecimal.ZERO
        );
        syncLinkedProjectProgress(appointment);
    }

    private PaymentDto processPaymentLines(ProcessPaymentRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        Appointment appointment = requireAppointment(request.getAppointmentId(), tenantId);
        appointmentAccessGuard.requireEdit(appointment);
        rejectCancelledAppointment(appointment);
        rejectReservationPayment(appointment);

        Staff processedBy = findProcessedByStaff(currentUserId, tenantId);
        AppointmentPaymentSummaryDto summary = summaryCalculator.calculate(appointment);
        List<PaymentLineRequest> lines = request.getLines();

        validatePaymentLines(lines, appointment, summary);

        Transaction lastPrimary = null;
        BigDecimal depositTotal = BigDecimal.ZERO;
        BigDecimal runningRemaining = summary.getRemainingBalance();

        for (PaymentLineRequest line : lines) {
            PaymentType paymentType = resolveLinePaymentType(line);
            PaymentMethod paymentMethod = PaymentMethod.fromValue(line.getPaymentMethod());
            String category = resolveCategory(line, paymentType);

            Transaction transaction = buildPaymentTransaction(
                    line.getAmount(),
                    line.getCashAmount(),
                    line.getCardAmount(),
                    null,
                    category,
                    request,
                    appointment,
                    paymentType,
                    paymentMethod,
                    processedBy,
                    tenantId
            );
            transaction = transactionRepository.save(transaction);

            log.info("Payment line processed: tenantId={} transactionId={} appointmentId={} method={}",
                    tenantId, transaction.getId(), appointment.getId(), paymentMethod.getValue());

            auditPayment(appointment, transaction);
            if (paymentType == PaymentType.SERVICE_PAYMENT || paymentType == PaymentType.DEPOSIT) {
                recordClientBalanceForPayment(
                        appointment,
                        transaction,
                        paymentMethod,
                        paymentType,
                        runningRemaining
                );
                runningRemaining = runningRemaining.subtract(line.getAmount()).max(BigDecimal.ZERO);
            }

            if (paymentType == PaymentType.DEPOSIT) {
                depositTotal = depositTotal.add(line.getAmount());
            }
            if (paymentType != PaymentType.TIP) {
                lastPrimary = transaction;
            }
        }

        applyDepositsTotal(depositTotal, appointment);
        recordTipIfPresent(request, appointment, resolveTipPaymentMethod(lines), processedBy, tenantId);
        syncLinkedProjectProgress(appointment);

        if (lastPrimary == null) {
            throw new BusinessRuleException("Payment batch must include at least one service or deposit line");
        }
        return paymentMapper.toDto(lastPrimary);
    }

    private boolean hasLines(ProcessPaymentRequest request) {
        return request.getLines() != null && !request.getLines().isEmpty();
    }

    private void validateLegacyRequest(ProcessPaymentRequest request) {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Payment amount must be greater than 0");
        }
        if (request.getPaymentMethod() == null || request.getPaymentMethod().isBlank()) {
            throw new BusinessRuleException("Payment method is required");
        }
    }

    private void validatePaymentLines(
            List<PaymentLineRequest> lines,
            Appointment appointment,
            AppointmentPaymentSummaryDto summary) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        for (PaymentLineRequest line : lines) {
            PaymentType paymentType = resolveLinePaymentType(line);
            PaymentMethod paymentMethod = PaymentMethod.fromValue(line.getPaymentMethod());

            if (paymentMethod == PaymentMethod.MONOBANK) {
                throw new BusinessRuleException("Monobank cannot be used in multi-line payment");
            }

            validatePaymentAmount(line.getAmount(), summary, paymentType);

            if (paymentMethod == PaymentMethod.BALANCE) {
                validateBalanceLine(appointment, tenantId, line.getAmount(), paymentType);
            }
        }
    }

    private void validateBalanceLine(
            Appointment appointment,
            UUID tenantId,
            BigDecimal amount,
            PaymentType paymentType) {
        if (paymentType == PaymentType.TIP || paymentType == PaymentType.REFUND) {
            throw new BusinessRuleException("Balance payment method cannot be used for this payment type");
        }

        Client client = requireAppointmentClient(appointment, tenantId);
        clientBalanceService.validateBalanceSpend(client, amount);
    }

    private PaymentType resolveLinePaymentType(PaymentLineRequest line) {
        if (line.getPaymentType() == null || line.getPaymentType().isBlank()) {
            return PaymentType.SERVICE_PAYMENT;
        }
        return PaymentType.fromValue(line.getPaymentType());
    }

    private String resolveCategory(PaymentLineRequest line, PaymentType paymentType) {
        if (line.getCategory() != null && !line.getCategory().isBlank()) {
            return line.getCategory();
        }
        return resolveCategoryForPaymentType(paymentType);
    }

    private String resolveCategoryForPaymentType(PaymentType paymentType) {
        if (paymentType == PaymentType.TIP) {
            return TransactionCategory.TIP.getValue();
        }
        return TransactionCategory.SERVICE.getValue();
    }

    private PaymentMethod resolveTipPaymentMethod(List<PaymentLineRequest> lines) {
        return lines.stream()
                .map(line -> PaymentMethod.fromValue(line.getPaymentMethod()))
                .filter(method -> method != PaymentMethod.BALANCE)
                .findFirst()
                .orElse(PaymentMethod.CASH);
    }

    private void applyDepositsTotal(BigDecimal depositTotal, Appointment appointment) {
        if (depositTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        appointment.setPrepayment(appointment.getPrepayment().add(depositTotal));
        appointmentRepository.save(appointment);
        auditRecorder.record(
                AuditAction.UPDATE,
                AuditEntityType.APPOINTMENT,
                appointment.getId().toString(),
                auditLabelFormatter.appointment(appointment.getClient(), appointment.getStartTime()),
                appointment.getClient() != null ? appointment.getClient().getId() : null,
                "Передоплата: +" + depositTotal
        );
    }

    private Appointment requireAppointment(UUID appointmentId, UUID tenantId) {
        return appointmentRepository.findByIdAndDeletedAtIsNull(appointmentId)
                .orElseThrow(() -> ResourceNotFoundException.appointment(appointmentId.toString()));
    }

    private void rejectCancelledAppointment(Appointment appointment) {
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new BusinessRuleException("Cannot process payment for cancelled appointment");
        }
    }

    private void rejectReservationPayment(Appointment appointment) {
        if (appointment.isReservation()) {
            throw BusinessRuleException.reservationPaymentNotAllowed();
        }
    }

    private Staff findProcessedByStaff(UUID currentUserId, UUID tenantId) {
        return staffRepository.findByIdAndDeletedAtIsNull(currentUserId)
                .orElse(null);
    }

    private PaymentType resolvePaymentType(ProcessPaymentRequest request) {
        if (request.getPaymentType() == null) {
            return PaymentType.SERVICE_PAYMENT;
        }
        return PaymentType.fromValue(request.getPaymentType());
    }

    private Transaction buildPaymentTransaction(
            BigDecimal amount,
            BigDecimal cashAmount,
            BigDecimal cardAmount,
            BigDecimal tipAmount,
            String category,
            ProcessPaymentRequest request,
            Appointment appointment,
            PaymentType paymentType,
            PaymentMethod paymentMethod,
            Staff processedBy,
            UUID tenantId) {
        return Transaction.builder()
                .tenantId(tenantId)
                .type(TransactionType.INCOME)
                .category(category)
                .paymentType(paymentType)
                .amount(amount)
                .paymentMethod(paymentMethod)
                .description(buildPaymentDescription(request, appointment, paymentType))
                .appointment(appointment)
                .staff(appointment.getArtist())
                .processedBy(processedBy)
                .location(appointment.getLocation())
                .date(Instant.now())
                .cashAmount(cashAmount)
                .cardAmount(cardAmount)
                .tipAmount(tipAmount)
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
        auditRecorder.record(
                AuditAction.TXN_INCOME,
                AuditEntityType.TRANSACTION,
                tipTransaction.getId().toString(),
                auditLabelFormatter.appointment(appointment.getClient(), appointment.getStartTime()),
                appointment.getClient() != null ? appointment.getClient().getId() : null,
                "Чайові: " + request.getTipAmount()
        );
    }

    private void applyDepositIfNeeded(BigDecimal amount, Appointment appointment, PaymentType paymentType) {
        if (paymentType != PaymentType.DEPOSIT) {
            return;
        }
        applyDepositsTotal(amount, appointment);
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

    private ProcessPaymentRequest normalizeSplitPayments(ProcessPaymentRequest request) {
        if (hasLines(request)) {
            List<PaymentLineRequest> expandedLines = new ArrayList<>();
            for (PaymentLineRequest line : request.getLines()) {
                expandedLines.addAll(expandSplitLine(line));
            }
            return request.toBuilder().lines(expandedLines).build();
        }

        if (request.getPaymentMethod() != null
                && PaymentMethod.SPLIT.getValue().equalsIgnoreCase(request.getPaymentMethod())) {
            List<PaymentLineRequest> lines = expandSplitAmounts(
                    request.getCashAmount(),
                    request.getCardAmount(),
                    request.getPaymentType(),
                    null
            );
            return request.toBuilder()
                    .lines(lines)
                    .paymentMethod(null)
                    .amount(null)
                    .cashAmount(null)
                    .cardAmount(null)
                    .build();
        }

        return request;
    }

    private List<PaymentLineRequest> expandSplitLine(PaymentLineRequest line) {
        if (line.getPaymentMethod() == null
                || !PaymentMethod.SPLIT.getValue().equalsIgnoreCase(line.getPaymentMethod())) {
            return List.of(line);
        }

        return expandSplitAmounts(
                line.getCashAmount(),
                line.getCardAmount(),
                line.getPaymentType(),
                line.getCategory()
        );
    }

    private List<PaymentLineRequest> expandSplitAmounts(
            BigDecimal cashAmount,
            BigDecimal cardAmount,
            String paymentType,
            String category) {
        validateSplitAmounts(cashAmount, cardAmount);

        List<PaymentLineRequest> lines = new ArrayList<>();
        if (isPositiveAmount(cashAmount)) {
            lines.add(PaymentLineRequest.builder()
                    .amount(cashAmount)
                    .paymentMethod(PaymentMethod.CASH.getValue())
                    .paymentType(paymentType)
                    .category(category)
                    .build());
        }
        if (isPositiveAmount(cardAmount)) {
            lines.add(PaymentLineRequest.builder()
                    .amount(cardAmount)
                    .paymentMethod(PaymentMethod.CARD.getValue())
                    .paymentType(paymentType)
                    .category(category)
                    .build());
        }

        if (lines.isEmpty()) {
            throw new BusinessRuleException("Split payment requires a positive cash or card amount");
        }

        return lines;
    }

    private void validateSplitAmounts(BigDecimal cashAmount, BigDecimal cardAmount) {
        if (cashAmount == null || cardAmount == null) {
            throw new BusinessRuleException("Split payment requires both cash and card amounts");
        }
    }

    private boolean isPositiveAmount(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }

    private String buildPaymentDescription(
            ProcessPaymentRequest request,
            Appointment appointment,
            PaymentType paymentType) {
        String typeLabel = paymentType == PaymentType.DEPOSIT ? "Передоплата" : "Оплата послуги";
        String serviceName = AppointmentLabels.serviceTitle(appointment);

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

    private void validateBalancePayment(Appointment appointment, BigDecimal amount, PaymentType paymentType) {
        validateBalanceLine(appointment, SecurityUtils.getCurrentTenantId(), amount, paymentType);
    }

    private Client requireAppointmentClient(Appointment appointment, UUID tenantId) {
        if (appointment.getClient() == null) {
            throw new BusinessRuleException("Appointment has no client for balance payment");
        }
        return clientRepository.findByIdAndDeletedAtIsNull(appointment.getClient().getId())
                .orElseThrow(() -> ResourceNotFoundException.client(appointment.getClient().getId().toString()));
    }

    private void recordClientBalanceForPayment(
            Appointment appointment,
            Transaction transaction,
            PaymentMethod paymentMethod,
            PaymentType paymentType,
            BigDecimal remainingBeforePayment) {
        if (appointment.getClient() == null) {
            return;
        }
        if (paymentType != PaymentType.SERVICE_PAYMENT && paymentType != PaymentType.DEPOSIT) {
            return;
        }

        Client client = clientRepository.findByIdAndDeletedAtIsNull(appointment.getClient().getId())
                .orElse(appointment.getClient());

        if (paymentMethod == PaymentMethod.BALANCE) {
            clientBalanceService.record(
                    client,
                    transaction.getAmount().negate(),
                    ClientBalanceReason.BALANCE_SPEND,
                    appointment.getId(),
                    transaction.getId(),
                    null
            );
            return;
        }

        if (paymentMethod.isRealMoney()) {
            BigDecimal balanceCredit = calculateBalanceCredit(transaction.getAmount(), remainingBeforePayment);
            if (balanceCredit.compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }

            clientBalanceService.record(
                    client,
                    balanceCredit,
                    ClientBalanceReason.PAYMENT,
                    appointment.getId(),
                    transaction.getId(),
                    null
            );
        }
    }

    private BigDecimal calculateBalanceCredit(BigDecimal paymentAmount, BigDecimal remainingBeforePayment) {
        if (remainingBeforePayment == null || remainingBeforePayment.compareTo(BigDecimal.ZERO) <= 0) {
            return paymentAmount;
        }

        BigDecimal appliedToAppointment = paymentAmount.min(remainingBeforePayment);
        return paymentAmount.subtract(appliedToAppointment);
    }

    @Transactional
    public void voidPayment(UUID transactionId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Transaction transaction = transactionRepository.findByIdAndDeletedAtIsNull(transactionId)
                .orElseThrow(() -> ResourceNotFoundException.transaction(transactionId.toString()));

        validateVoidable(transaction);

        Appointment appointment = transaction.getAppointment();
        appointmentAccessGuard.requireEdit(appointment);

        reverseBalanceForVoid(transaction);
        adjustDepositAfterVoid(transaction);

        transaction.softDelete();
        transactionRepository.save(transaction);

        log.info("Payment voided: tenantId={} transactionId={} appointmentId={}",
                tenantId, transaction.getId(), appointment.getId());

        auditRecorder.record(
                AuditAction.DELETE,
                AuditEntityType.TRANSACTION,
                transaction.getId().toString(),
                auditLabelFormatter.appointment(appointment.getClient(), appointment.getStartTime()),
                appointment.getClient() != null ? appointment.getClient().getId() : null,
                "Void payment: " + transaction.getAmount()
        );

        syncLinkedProjectProgress(appointment);
    }

    private void validateVoidable(Transaction transaction) {
        if (transaction.isRefund() || transaction.getPaymentType() == PaymentType.TIP) {
            throw new BusinessRuleException("This payment cannot be deleted");
        }
        if (transaction.getPaymentType() != PaymentType.SERVICE_PAYMENT
                && transaction.getPaymentType() != PaymentType.DEPOSIT) {
            throw new BusinessRuleException("This payment cannot be deleted");
        }
        if (transaction.getRefundedAmount() != null
                && transaction.getRefundedAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessRuleException("Cannot delete a payment that has refunds");
        }
        if (transaction.getAppointment() == null) {
            throw new BusinessRuleException("Payment is not linked to an appointment");
        }
    }

    private void reverseBalanceForVoid(Transaction transaction) {
        Appointment appointment = transaction.getAppointment();
        if (appointment == null || appointment.getClient() == null) {
            return;
        }

        Client client = clientRepository.findByIdAndDeletedAtIsNull(appointment.getClient().getId())
                .orElse(appointment.getClient());

        balanceEntryRepository.findByTransactionIdAndDeletedAtIsNull(transaction.getId())
                .ifPresent(entry -> clientBalanceService.record(
                        client,
                        entry.getAmount().negate(),
                        ClientBalanceReason.PAYMENT_VOID,
                        appointment.getId(),
                        transaction.getId(),
                        null
                ));
    }

    private void adjustDepositAfterVoid(Transaction transaction) {
        if (transaction.getPaymentType() != PaymentType.DEPOSIT) {
            return;
        }

        Appointment appointment = transaction.getAppointment();
        if (appointment == null) {
            return;
        }

        BigDecimal newPrepayment = appointment.getPrepayment().subtract(transaction.getAmount());
        appointment.setPrepayment(newPrepayment.max(BigDecimal.ZERO));
        appointmentRepository.save(appointment);
    }

    private void auditPayment(Appointment appointment, Transaction transaction) {
        UUID clientId = appointment.getClient() != null ? appointment.getClient().getId() : null;
        String details = transaction.getAmount() + " " + transaction.getPaymentMethod().getValue();
        auditRecorder.record(
                AuditAction.PAYMENT,
                AuditEntityType.APPOINTMENT,
                appointment.getId().toString(),
                auditLabelFormatter.appointment(appointment.getClient(), appointment.getStartTime()),
                clientId,
                details
        );
    }
}
