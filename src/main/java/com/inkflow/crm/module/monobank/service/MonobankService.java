package com.inkflow.crm.module.monobank.service;

import com.inkflow.crm.config.BypassTenantFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.*;
import com.inkflow.crm.domain.enums.*;
import com.inkflow.crm.domain.repository.*;
import com.inkflow.crm.module.monobank.config.MonobankConfig;
import com.inkflow.crm.module.monobank.dto.*;
import com.inkflow.crm.module.appointment.support.AppointmentLabels;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.audit.support.AuditLabelFormatter;
import com.inkflow.crm.module.subscription.service.SubscriptionService;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonobankService {

    private final MonobankConfig config;
    private final MonobankInvoiceRepository invoiceRepository;
    private final AppointmentRepository appointmentRepository;
    private final TransactionRepository transactionRepository;
    private final StaffRepository staffRepository;
    private final ObjectMapper objectMapper;
    private final SubscriptionService subscriptionService;
    private final AuditRecorder auditRecorder;
    private final AuditLabelFormatter auditLabelFormatter;

    private static final int CCY_UAH = 980;

    @Transactional
    public OnlineInvoiceDto createInvoice(CreateOnlineInvoiceRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Appointment appointment = appointmentRepository
                .findByIdAndDeletedAtIsNull(request.getAppointmentId())
                .orElseThrow(() -> ResourceNotFoundException.appointment(request.getAppointmentId().toString()));

        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new BusinessRuleException("Cannot create invoice for cancelled appointment");
        }

        invoiceRepository.findByAppointmentIdAndStatus(request.getAppointmentId(), "pending")
                .ifPresent(old -> {
                    old.setStatus("cancelled");
                    invoiceRepository.save(old);
                });

        long amountKopecks = request.getAmount().multiply(BigDecimal.valueOf(100)).longValue();

        String clientName = appointment.getClient() != null
                ? appointment.getClient().getFirstName() + " " + appointment.getClient().getLastName()
                : "Клієнт";
        String serviceName = AppointmentLabels.serviceTitle(appointment);

        Map<String, Object> body = Map.of(
                "amount", amountKopecks,
                "ccy", CCY_UAH,
                "merchantPaymInfo", Map.of(
                        "reference", request.getAppointmentId().toString(),
                        "destination", buildDestination(request.getPaymentType(), serviceName),
                        "comment", "InkFlow Studio — " + clientName
                ),
                "redirectUrl", config.getRedirectUrl(),
                "webHookUrl", config.getWebhookUrl(),
                "validity", config.getInvoiceValiditySeconds(),
                "paymentType", "debit"
        );

        Map<String, Object> monobankResponse = callMonobankApi(
                "POST",
                "/api/merchant/invoice/create",
                body
        );

        String invoiceId = (String) monobankResponse.get("invoiceId");
        String pageUrl  = (String) monobankResponse.get("pageUrl");

        Instant expiresAt = Instant.now().plusSeconds(config.getInvoiceValiditySeconds());

        MonobankInvoice invoice = MonobankInvoice.builder()
                .tenantId(tenantId)
                .appointmentId(request.getAppointmentId())
                .monobankInvoiceId(invoiceId)
                .amount(request.getAmount())
                .ccy(CCY_UAH)
                .pageUrl(pageUrl)
                .status("pending")
                .paymentType(request.getPaymentType())
                .expiresAt(expiresAt)
                .build();

        invoice = invoiceRepository.save(invoice);

        log.info("Monobank invoice created: {} for appointment {}", invoiceId, request.getAppointmentId());
        return toDto(invoice);
    }

    @BypassTenantFilter
    @Transactional
    public void handleWebhook(MonobankWebhookPayload payload) {
        if (payload == null || payload.getInvoiceId() == null || payload.getInvoiceId().isBlank()) {
            log.warn("Monobank webhook ignored — missing invoiceId");
            return;
        }

        log.info("Monobank webhook received: invoiceId={} status={}", payload.getInvoiceId(), payload.getStatus());

        MonobankInvoice invoice = invoiceRepository.findByMonobankInvoiceId(payload.getInvoiceId())
                .orElse(null);

        if (invoice == null) {
            log.warn("Unknown Monobank invoiceId: {}", payload.getInvoiceId());
            return;
        }

        if ("success".equals(invoice.getStatus()) && invoice.getTransactionId() != null) {
            log.info("Monobank webhook ignored — invoice already processed: {}", payload.getInvoiceId());
            return;
        }

        if ("success".equals(payload.getStatus()) && !verifySuccessPayload(invoice, payload)) {
            return;
        }

        invoice.setStatus(payload.getStatus());

        if ("success".equals(payload.getStatus())) {
            invoice.setPaidAt(Instant.now());
            if ("SUBSCRIPTION".equals(invoice.getInvoiceType())) {
                subscriptionService.activateSubscription(invoice.getTenantId(), payload.getInvoiceId());
            } else if (invoice.getTransactionId() == null) {
                recordSuccessfulPayment(invoice, payload);
            }
        }

        invoiceRepository.save(invoice);
    }

    private boolean verifySuccessPayload(MonobankInvoice invoice, MonobankWebhookPayload payload) {
        if (!amountMatches(invoice, payload)) {
            log.warn("Monobank amount mismatch for invoice {}: expected={} received={}",
                    payload.getInvoiceId(), invoice.getAmount(), payload.getAmountDecimal());
            return false;
        }

        if (config.getToken() == null || config.getToken().startsWith("REPLACE_")) {
            return true;
        }

        String remoteStatus = fetchRemoteInvoiceStatus(payload.getInvoiceId());
        if (remoteStatus != null && !"success".equals(remoteStatus)) {
            log.warn("Monobank remote status mismatch for invoice {}: {}", payload.getInvoiceId(), remoteStatus);
            return false;
        }

        return true;
    }

    private boolean amountMatches(MonobankInvoice invoice, MonobankWebhookPayload payload) {
        long expectedKopecks = invoice.getAmount().multiply(BigDecimal.valueOf(100)).longValue();
        Long receivedKopecks = payload.getFinalAmount() != null ? payload.getFinalAmount() : payload.getAmount();
        return receivedKopecks == null || receivedKopecks == expectedKopecks;
    }

    @SuppressWarnings("unchecked")
    private String fetchRemoteInvoiceStatus(String invoiceId) {
        try {
            Map<String, Object> response = callMonobankApi("GET", "/api/merchant/invoice/status?invoiceId=" + invoiceId, null);
            Object status = response.get("status");
            return status != null ? status.toString() : null;
        } catch (Exception e) {
            log.warn("Failed to verify Monobank invoice status for {}: {}", invoiceId, e.getMessage());
            return null;
        }
    }

    private void recordSuccessfulPayment(MonobankInvoice invoice, MonobankWebhookPayload payload) {
        Appointment appointment = appointmentRepository
                .findByIdAndDeletedAtIsNull(invoice.getAppointmentId())
                .orElse(null);

        if (appointment == null) {
            log.error("Appointment {} not found for Monobank invoice {}", invoice.getAppointmentId(), invoice.getMonobankInvoiceId());
            return;
        }

        PaymentType paymentType = "deposit".equals(invoice.getPaymentType())
                ? PaymentType.DEPOSIT
                : PaymentType.SERVICE_PAYMENT;

        String receiptNumber = "MONO-" + invoice.getMonobankInvoiceId();

        Transaction transaction = Transaction.builder()
                .tenantId(invoice.getTenantId())
                .type(TransactionType.INCOME)
                .category(TransactionCategory.SERVICE.getValue())
                .paymentType(paymentType)
                .amount(invoice.getAmount())
                .paymentMethod(PaymentMethod.MONOBANK)
                .description(buildDescription(paymentType, appointment, payload))
                .appointment(appointment)
                .staff(appointment.getArtist())
                .location(appointment.getLocation())
                .date(Instant.now())
                .receiptNumber(receiptNumber)
                .isRefunded(false)
                .refundedAmount(BigDecimal.ZERO)
                .build();

        transaction = transactionRepository.save(transaction);
        invoice.setTransactionId(transaction.getId());

        if (paymentType == PaymentType.DEPOSIT) {
            appointment.setPrepayment(appointment.getPrepayment().add(invoice.getAmount()));
            appointmentRepository.save(appointment);
        }

        log.info("Transaction {} recorded for Monobank invoice {}", transaction.getId(), invoice.getMonobankInvoiceId());

        UUID clientId = appointment.getClient() != null ? appointment.getClient().getId() : null;
        auditRecorder.recordSystem(
                invoice.getTenantId(),
                AuditAction.PAYMENT,
                AuditEntityType.APPOINTMENT,
                appointment.getId().toString(),
                auditLabelFormatter.appointment(appointment.getClient(), appointment.getStartTime()),
                clientId,
                "Monobank · " + invoice.getAmount() + " ₴"
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callMonobankApi(String method, String path, Object body) {
        if (config.getToken() == null || config.getToken().startsWith("REPLACE_")) {
            if ("GET".equals(method)) {
                return Map.of("status", "success");
            }
            log.warn("Monobank token not configured — returning sandbox invoice");
            String fakeId = "sandbox_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            return Map.of(
                    "invoiceId", fakeId,
                    "pageUrl", "https://pay.monobank.ua/sandbox/" + fakeId
            );
        }

        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(config.getApiUrl() + path))
                    .header("X-Token", config.getToken());

            if ("GET".equals(method)) {
                requestBuilder.GET();
            } else {
                String requestBody = objectMapper.writeValueAsString(body);
                requestBuilder
                        .header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(requestBody));
            }

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Monobank API error {}: {}", response.statusCode(), response.body());
                throw new BusinessRuleException("Monobank API error: " + response.statusCode());
            }

            return objectMapper.readValue(response.body(), Map.class);

        } catch (BusinessRuleException e) {
            throw e;
        } catch (Exception e) {
            log.error("Monobank API call failed", e);
            throw new BusinessRuleException("Failed to communicate with Monobank: " + e.getMessage());
        }
    }

    private String buildDestination(String paymentType, String serviceName) {
        return "deposit".equals(paymentType)
                ? "Передоплата: " + serviceName
                : "Оплата: " + serviceName;
    }

    private String buildDescription(PaymentType type, Appointment appointment, MonobankWebhookPayload payload) {
        String serviceName = AppointmentLabels.serviceTitle(appointment);
        String prefix = type == PaymentType.DEPOSIT ? "Передоплата (Monobank)" : "Оплата (Monobank)";
        String pan = payload.getPaymentInfo() != null ? " • " + payload.getPaymentInfo().getMaskedPan() : "";
        return prefix + ": " + serviceName + pan;
    }

    private OnlineInvoiceDto toDto(MonobankInvoice invoice) {
        return OnlineInvoiceDto.builder()
                .id(invoice.getId())
                .appointmentId(invoice.getAppointmentId())
                .monobankInvoiceId(invoice.getMonobankInvoiceId())
                .amount(invoice.getAmount())
                .pageUrl(invoice.getPageUrl())
                .status(invoice.getStatus())
                .paymentType(invoice.getPaymentType())
                .expiresAt(invoice.getExpiresAt())
                .createdAt(invoice.getCreatedAt())
                .build();
    }
}
