package com.inkflow.crm.module.subscription.service;

import com.inkflow.crm.config.BypassTenantFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.domain.entity.Subscription;
import com.inkflow.crm.domain.repository.SubscriptionRepository;
import com.inkflow.crm.module.monobank.config.MonobankConfig;
import com.inkflow.crm.module.subscription.dto.SubscriptionCheckoutResponse;
import com.inkflow.crm.module.subscription.dto.SubscriptionDto;
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
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final int TRIAL_DAYS = 14;
    private static final int SUBSCRIPTION_PERIOD_DAYS = 30;
    private static final BigDecimal STANDARD_PRICE = BigDecimal.valueOf(399);
    private static final int CCY_UAH = 980;

    private final SubscriptionRepository subscriptionRepository;
    private final MonobankConfig monobankConfig;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public SubscriptionDto getCurrentSubscription() {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Subscription sub = getOrCreateTrial(tenantId);
        return toDto(sub);
    }

    @BypassTenantFilter
    @Transactional
    public boolean isSubscriptionActive(UUID tenantId) {
        return subscriptionRepository.findByTenantId(tenantId)
                .map(Subscription::isActive)
                .orElseGet(() -> {
                    createTrialForTenant(tenantId);
                    return true;
                });
    }

    @Transactional
    public SubscriptionCheckoutResponse createCheckout() {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Subscription sub = getOrCreateTrial(tenantId);

        long amountKopecks = STANDARD_PRICE.multiply(BigDecimal.valueOf(100)).longValue();

        Map<String, Object> body = Map.of(
                "amount", amountKopecks,
                "ccy", CCY_UAH,
                "merchantPaymInfo", Map.of(
                        "reference", "subscription_" + tenantId,
                        "destination", "Підписка InKat CRM — 1 місяць",
                        "comment", "InKat CRM — Standard Plan"
                ),
                "redirectUrl", monobankConfig.getRedirectUrl(),
                "webHookUrl", monobankConfig.getWebhookUrl(),
                "validity", 1800,
                "paymentType", "debit"
        );

        Map<String, Object> response = callMonobankApi(body);
        String invoiceId = (String) response.get("invoiceId");
        String pageUrl   = (String) response.get("pageUrl");

        sub.setLastInvoiceId(invoiceId);
        subscriptionRepository.save(sub);

        log.info("Subscription checkout created for tenant {}: invoiceId={}", tenantId, invoiceId);
        return SubscriptionCheckoutResponse.builder()
                .invoiceId(invoiceId)
                .pageUrl(pageUrl)
                .plan("STANDARD")
                .build();
    }

    @Transactional
    public void activateSubscription(UUID tenantId, String monobankInvoiceId) {
        Subscription sub = subscriptionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new BusinessRuleException("Subscription not found for tenant: " + tenantId));

        Instant now = Instant.now();
        Instant periodStart = sub.isActive() && "STANDARD".equals(sub.getPlan())
                ? sub.getCurrentPeriodEnd()
                : now;

        sub.setPlan("STANDARD");
        sub.setStatus("ACTIVE");
        sub.setCurrentPeriodEnd(periodStart.plus(SUBSCRIPTION_PERIOD_DAYS, ChronoUnit.DAYS));
        sub.setLastInvoiceId(monobankInvoiceId);
        subscriptionRepository.save(sub);

        log.info("Subscription activated for tenant {}, valid until {}", tenantId, sub.getCurrentPeriodEnd());
    }

    @BypassTenantFilter
    @Transactional
    public Subscription createTrialForTenant(UUID tenantId) {
        return subscriptionRepository.findByTenantId(tenantId).orElseGet(() -> {
            Subscription sub = Subscription.builder()
                    .tenantId(tenantId)
                    .plan("TRIAL")
                    .status("ACTIVE")
                    .trialEndsAt(Instant.now().plus(TRIAL_DAYS, ChronoUnit.DAYS))
                    .monthlyPrice(STANDARD_PRICE)
                    .build();
            log.info("Created trial subscription for tenant {}, expires {}", tenantId, sub.getTrialEndsAt());
            return subscriptionRepository.save(sub);
        });
    }

    private Subscription getOrCreateTrial(UUID tenantId) {
        return subscriptionRepository.findByTenantId(tenantId)
                .orElseGet(() -> createTrialForTenant(tenantId));
    }

    private SubscriptionDto toDto(Subscription sub) {
        return SubscriptionDto.builder()
                .id(sub.getId())
                .plan(sub.getPlan())
                .status(sub.getStatus())
                .active(sub.isActive())
                .daysRemaining(sub.daysRemaining())
                .trialEndsAt(sub.getTrialEndsAt())
                .currentPeriodEnd(sub.getCurrentPeriodEnd())
                .monthlyPrice(sub.getMonthlyPrice())
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callMonobankApi(Object body) {
        if (monobankConfig.getToken() == null || monobankConfig.getToken().startsWith("REPLACE_")) {
            String fakeId = "sandbox_sub_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
            return Map.of("invoiceId", fakeId, "pageUrl", "https://pay.monobank.ua/sandbox/" + fakeId);
        }
        try {
            String requestBody = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(monobankConfig.getApiUrl() + "/api/merchant/invoice/create"))
                    .header("X-Token", monobankConfig.getToken())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new BusinessRuleException("Monobank API error: " + response.statusCode());
            }
            return objectMapper.readValue(response.body(), Map.class);
        } catch (BusinessRuleException e) {
            throw e;
        } catch (Exception e) {
            log.error("Subscription checkout failed", e);
            throw new BusinessRuleException("Payment service unavailable: " + e.getMessage());
        }
    }
}
