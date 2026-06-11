package com.inkflow.crm.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.module.subscription.service.SubscriptionService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionFilterTest {

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private SubscriptionFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{\"error\":\"SUBSCRIPTION_EXPIRED\"}");
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldPassThroughForReadOnlyMethods() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clients");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(subscriptionService, never()).isSubscriptionActive(any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldPassThroughForAllowedWritePrefixes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/onboarding/step");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(subscriptionService, never()).isSubscriptionActive(any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldPassThroughWhenUserNotAuthenticated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/clients");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(subscriptionService, never()).isSubscriptionActive(any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldBlockWriteRequestsWhenSubscriptionInactive() throws Exception {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);
        when(subscriptionService.isSubscriptionActive(tenantId)).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/clients");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(402, response.getStatus());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldAllowWriteRequestsWhenSubscriptionActive() throws Exception {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);
        when(subscriptionService.isSubscriptionActive(tenantId)).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/clients/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldPassThroughWhenSubscriptionCheckFails() throws Exception {
        authenticate(UUID.randomUUID());
        when(subscriptionService.isSubscriptionActive(any())).thenThrow(new RuntimeException("db down"));

        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/clients/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    private void authenticate(UUID tenantId) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .role(UserRole.OWNER)
                .email("owner@test.com")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
