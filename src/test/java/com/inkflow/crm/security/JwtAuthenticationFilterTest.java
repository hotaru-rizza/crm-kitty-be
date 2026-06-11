package com.inkflow.crm.security;

import com.inkflow.crm.domain.enums.UserRole;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @AfterEach
    void clearContexts() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void shouldAuthenticateAndPopulateTenantContextWhenTokenValid() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.builder()
                .id(userId)
                .tenantId(tenantId)
                .role(UserRole.ADMIN)
                .locationIds(List.of(locationId))
                .email("admin@test.com")
                .build();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(tokenProvider.validateToken("valid-token")).thenReturn(true);
        when(tokenProvider.getUserPrincipal("valid-token")).thenReturn(principal);

        filter.doFilter(request, response, filterChain);

        assertEquals(principal, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        verify(filterChain).doFilter(request, response);
        assertNull(TenantContext.getCurrentTenant());
    }

    @Test
    void shouldContinueWithoutAuthenticationWhenNoToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clients");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(tokenProvider, never()).validateToken(org.mockito.ArgumentMatchers.anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldContinueWithoutAuthenticationWhenTokenInvalid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer expired-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(tokenProvider.validateToken("expired-token")).thenReturn(false);

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(tokenProvider, never()).getUserPrincipal(org.mockito.ArgumentMatchers.anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldContinueWhenTokenProviderThrows() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer broken-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(tokenProvider.validateToken("broken-token")).thenReturn(true);
        doThrow(new RuntimeException("jwks unavailable"))
                .when(tokenProvider).getUserPrincipal("broken-token");

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
        assertNull(TenantContext.getCurrentTenant());
    }

    @Test
    void shouldPopulateTenantContextWhileFilterChainRuns() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.builder()
                .id(userId)
                .tenantId(tenantId)
                .role(UserRole.ADMIN)
                .locationIds(List.of(UUID.randomUUID()))
                .email("admin@test.com")
                .build();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(tokenProvider.validateToken("valid-token")).thenReturn(true);
        when(tokenProvider.getUserPrincipal("valid-token")).thenReturn(principal);
        doAnswer(invocation -> {
            assertEquals(tenantId, TenantContext.getCurrentTenant());
            assertEquals(userId, TenantContext.getCurrentUser());
            assertEquals(UserRole.ADMIN, TenantContext.getCurrentRole());
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilter(request, response, filterChain);

        assertNull(TenantContext.getCurrentTenant());
    }

    @Test
    void shouldIgnoreBearerTokenWithEmptyPayload() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(tokenProvider, never()).validateToken(org.mockito.ArgumentMatchers.anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldContinueWhenValidateTokenThrows() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer risky-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(tokenProvider.validateToken("risky-token")).thenThrow(new RuntimeException("jwks timeout"));

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldIgnoreAuthorizationHeaderWithoutBearerPrefix() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(tokenProvider, never()).validateToken(org.mockito.ArgumentMatchers.anyString());
        verify(filterChain).doFilter(request, response);
        assertTrue(SecurityContextHolder.getContext().getAuthentication() == null);
    }
}
