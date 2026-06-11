package com.inkflow.crm.module.subscription.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.domain.entity.Subscription;
import com.inkflow.crm.domain.repository.SubscriptionRepository;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.module.monobank.config.MonobankConfig;
import com.inkflow.crm.module.subscription.dto.SubscriptionCheckoutResponse;
import com.inkflow.crm.module.subscription.dto.SubscriptionDto;
import com.inkflow.crm.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private MonobankConfig monobankConfig;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private SubscriptionService subscriptionService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldPersistTrialSubscriptionWhenTenantHasNone() {
        UUID tenantId = UUID.randomUUID();

        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Subscription subscription = subscriptionService.createTrialForTenant(tenantId);

        assertEquals("TRIAL", subscription.getPlan());
        assertEquals("ACTIVE", subscription.getStatus());
        assertEquals(tenantId, subscription.getTenantId());
        assertTrue(subscription.getTrialEndsAt().isAfter(Instant.now()));
        assertEquals(BigDecimal.valueOf(399), subscription.getMonthlyPrice());
    }

    @Test
    void shouldReturnExistingTrialWhenTenantAlreadyHasSubscription() {
        UUID tenantId = UUID.randomUUID();
        Subscription existing = Subscription.builder()
                .tenantId(tenantId)
                .plan("TRIAL")
                .status("ACTIVE")
                .trialEndsAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();

        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.of(existing));

        Subscription result = subscriptionService.createTrialForTenant(tenantId);

        assertEquals(existing, result);
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void shouldActivateStandardPlanFromNowWhenTrialExpires() {
        UUID tenantId = UUID.randomUUID();
        Subscription existing = Subscription.builder()
                .tenantId(tenantId)
                .plan("TRIAL")
                .status("ACTIVE")
                .build();

        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.of(existing));
        when(subscriptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        subscriptionService.activateSubscription(tenantId, "mono-inv-1");

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());

        Subscription saved = captor.getValue();
        assertEquals("STANDARD", saved.getPlan());
        assertEquals("ACTIVE", saved.getStatus());
        assertEquals("mono-inv-1", saved.getLastInvoiceId());
        assertTrue(saved.getCurrentPeriodEnd().isAfter(Instant.now().plus(29, ChronoUnit.DAYS)));
    }

    @Test
    void shouldExtendFromCurrentPeriodEndWhenStandardPlanStillActive() {
        UUID tenantId = UUID.randomUUID();
        Instant currentPeriodEnd = Instant.now().plus(10, ChronoUnit.DAYS);
        Subscription existing = Subscription.builder()
                .tenantId(tenantId)
                .plan("STANDARD")
                .status("ACTIVE")
                .currentPeriodEnd(currentPeriodEnd)
                .build();

        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.of(existing));
        when(subscriptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        subscriptionService.activateSubscription(tenantId, "mono-inv-renewal");

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());

        Subscription saved = captor.getValue();
        assertEquals(currentPeriodEnd.plus(30, ChronoUnit.DAYS), saved.getCurrentPeriodEnd());
        assertEquals("mono-inv-renewal", saved.getLastInvoiceId());
    }

    @Test
    void shouldThrowWhenActivatingMissingSubscription() {
        UUID tenantId = UUID.randomUUID();
        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());

        assertThrows(BusinessRuleException.class,
                () -> subscriptionService.activateSubscription(tenantId, "mono-inv-missing"));
    }

    @Test
    void shouldCreateTrialAndReturnTrueWhenTenantHasNoSubscription() {
        UUID tenantId = UUID.randomUUID();

        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertTrue(subscriptionService.isSubscriptionActive(tenantId));
        verify(subscriptionRepository).save(any(Subscription.class));
    }

    @Test
    void shouldReturnFalseWhenTrialHasExpired() {
        UUID tenantId = UUID.randomUUID();
        Subscription expiredTrial = Subscription.builder()
                .tenantId(tenantId)
                .plan("TRIAL")
                .status("ACTIVE")
                .trialEndsAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();

        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.of(expiredTrial));

        assertFalse(subscriptionService.isSubscriptionActive(tenantId));
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void shouldMapCurrentSubscriptionForAuthenticatedTenant() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);
        Subscription subscription = Subscription.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .plan("TRIAL")
                .status("ACTIVE")
                .trialEndsAt(Instant.now().plus(14, ChronoUnit.DAYS))
                .monthlyPrice(BigDecimal.valueOf(399))
                .build();

        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.of(subscription));

        SubscriptionDto dto = subscriptionService.getCurrentSubscription();

        assertEquals(subscription.getId(), dto.getId());
        assertEquals("TRIAL", dto.getPlan());
        assertEquals("ACTIVE", dto.getStatus());
        assertTrue(dto.isActive());
        assertEquals(BigDecimal.valueOf(399), dto.getMonthlyPrice());
        assertTrue(dto.getDaysRemaining() > 0);
    }

    @Test
    void shouldCreateSandboxCheckoutAndPersistInvoiceId() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);
        Subscription subscription = Subscription.builder()
                .tenantId(tenantId)
                .plan("TRIAL")
                .status("ACTIVE")
                .trialEndsAt(Instant.now().plus(14, ChronoUnit.DAYS))
                .build();

        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(monobankConfig.getToken()).thenReturn("REPLACE_TEST");
        when(monobankConfig.getRedirectUrl()).thenReturn("http://localhost/redirect");
        when(monobankConfig.getWebhookUrl()).thenReturn("http://localhost/webhook");

        SubscriptionCheckoutResponse checkout = subscriptionService.createCheckout();

        assertEquals("STANDARD", checkout.getPlan());
        assertNotNull(checkout.getInvoiceId());
        assertTrue(checkout.getInvoiceId().startsWith("sandbox_sub_"));
        assertTrue(checkout.getPageUrl().contains(checkout.getInvoiceId()));

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertEquals(checkout.getInvoiceId(), captor.getValue().getLastInvoiceId());
    }

    @Test
    void shouldReturnTrueWhenStandardPlanIsWithinPeriod() {
        UUID tenantId = UUID.randomUUID();
        Subscription activeStandard = Subscription.builder()
                .tenantId(tenantId)
                .plan("STANDARD")
                .status("ACTIVE")
                .currentPeriodEnd(Instant.now().plus(10, ChronoUnit.DAYS))
                .build();

        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.of(activeStandard));

        assertTrue(subscriptionService.isSubscriptionActive(tenantId));
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void shouldReturnFalseWhenStandardPlanHasExpired() {
        UUID tenantId = UUID.randomUUID();
        Subscription expiredStandard = Subscription.builder()
                .tenantId(tenantId)
                .plan("STANDARD")
                .status("ACTIVE")
                .currentPeriodEnd(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();

        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.of(expiredStandard));

        assertFalse(subscriptionService.isSubscriptionActive(tenantId));
    }

    @Test
    void shouldReturnFalseWhenSubscriptionIsCancelled() {
        UUID tenantId = UUID.randomUUID();
        Subscription cancelled = Subscription.builder()
                .tenantId(tenantId)
                .plan("STANDARD")
                .status("CANCELLED")
                .currentPeriodEnd(Instant.now().plus(10, ChronoUnit.DAYS))
                .build();

        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.of(cancelled));

        assertFalse(subscriptionService.isSubscriptionActive(tenantId));
    }

    @Test
    void shouldActivateFromNowWhenStandardPlanHasExpired() {
        UUID tenantId = UUID.randomUUID();
        Instant expiredEnd = Instant.now().minus(2, ChronoUnit.DAYS);
        Subscription lapsed = Subscription.builder()
                .tenantId(tenantId)
                .plan("STANDARD")
                .status("ACTIVE")
                .currentPeriodEnd(expiredEnd)
                .build();

        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.of(lapsed));
        when(subscriptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        subscriptionService.activateSubscription(tenantId, "mono-inv-reactivate");

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());

        Subscription saved = captor.getValue();
        assertTrue(saved.getCurrentPeriodEnd().isAfter(Instant.now().plus(29, ChronoUnit.DAYS)));
        assertNotEquals(expiredEnd.plus(30, ChronoUnit.DAYS), saved.getCurrentPeriodEnd());
    }

    @Test
    void shouldCreateTrialWhenGettingCurrentSubscriptionForNewTenant() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionDto dto = subscriptionService.getCurrentSubscription();

        assertEquals("TRIAL", dto.getPlan());
        assertTrue(dto.isActive());
        verify(subscriptionRepository).save(any(Subscription.class));
    }

    @Test
    void shouldCreateTrialDuringCheckoutWhenTenantHasNoSubscription() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(monobankConfig.getToken()).thenReturn("REPLACE_TEST");
        when(monobankConfig.getRedirectUrl()).thenReturn("http://localhost/redirect");
        when(monobankConfig.getWebhookUrl()).thenReturn("http://localhost/webhook");

        SubscriptionCheckoutResponse checkout = subscriptionService.createCheckout();

        assertNotNull(checkout.getInvoiceId());
        verify(subscriptionRepository, times(2)).save(any(Subscription.class));
    }

    private void authenticate(UUID tenantId) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .role(UserRole.OWNER)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
