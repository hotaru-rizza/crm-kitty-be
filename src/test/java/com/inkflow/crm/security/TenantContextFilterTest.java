package com.inkflow.crm.security;

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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;

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
    void shouldSetDbTenantWhenTenantSet() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setCurrentTenant(tenantId);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clients");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);

        filter.doFilter(request, response, filterChain);

        verify(preparedStatement).setString(1, tenantId.toString());
        verify(preparedStatement).execute();
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
