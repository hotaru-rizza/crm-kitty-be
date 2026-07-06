package com.inkflow.crm.security;

import com.inkflow.crm.common.exception.AccessDeniedException;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LocationContextFilterTest {

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private LocationContextFilter filter;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldSetLocationFromHeaderWhenAccessible() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .role(UserRole.ADMIN)
                .locationIds(List.of(locationId))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
        TenantContext.setCurrentTenant(tenantId);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clients");
        request.addHeader("X-Location-Id", locationId.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(locationId, TenantContext.getCurrentLocation());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldThrowWhenLocationHeaderNotAccessible() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID allowedLocation = UUID.randomUUID();
        UUID deniedLocation = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .role(UserRole.ARTIST)
                .locationIds(List.of(allowedLocation))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
        TenantContext.setCurrentTenant(tenantId);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clients");
        request.addHeader("X-Location-Id", deniedLocation.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(AccessDeniedException.class, () -> filter.doFilter(request, response, filterChain));
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldIgnoreInvalidLocationHeaderAndContinue() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setCurrentTenant(tenantId);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clients");
        request.addHeader("X-Location-Id", "not-a-uuid");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertNull(TenantContext.getCurrentLocation());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldAllowAdminForAnyLocationHeader() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID unrestrictedLocation = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .role(UserRole.ADMIN)
                .locationIds(List.of())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
        TenantContext.setCurrentTenant(tenantId);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clients");
        request.addHeader("X-Location-Id", unrestrictedLocation.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(unrestrictedLocation, TenantContext.getCurrentLocation());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldSetLocationWhenNoAuthenticatedUser() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        TenantContext.setCurrentTenant(tenantId);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clients");
        request.addHeader("X-Location-Id", locationId.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(locationId, TenantContext.getCurrentLocation());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldSetEntityScopeAndSkipLocationHeader() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        TenantContext.setCurrentTenant(tenantId);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/appointments");
        request.addHeader(LocationScope.ENTITY_SCOPE_HEADER, "true");
        request.addHeader("X-Location-Id", locationId.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertTrue(TenantContext.isEntityScope());
        assertNull(TenantContext.getCurrentLocation());
        verify(filterChain).doFilter(request, response);
    }
}
