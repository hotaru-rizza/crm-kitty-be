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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantContextFilterTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement preparedStatement;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private TenantContextFilter filter;

    @AfterEach
    void clearContexts() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void shouldPassThroughWhenTenantNotSet() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clients");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(dataSource, never()).getConnection();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldSetDbTenantAndLocationWhenUserHasAccess() throws Exception {
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

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);

        filter.doFilter(request, response, filterChain);

        verify(preparedStatement).setString(1, tenantId.toString());
        verify(preparedStatement).execute();
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

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);

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

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);

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

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);

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

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);

        filter.doFilter(request, response, filterChain);

        assertEquals(locationId, TenantContext.getCurrentLocation());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldContinueWhenPreparedStatementExecuteFails() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setCurrentTenant(tenantId);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clients");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        doThrow(new RuntimeException("set_config failed")).when(preparedStatement).execute();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldContinueWhenDbTenantSetupFails() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setCurrentTenant(tenantId);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clients");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(dataSource.getConnection()).thenThrow(new RuntimeException("db unavailable"));

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }
}
