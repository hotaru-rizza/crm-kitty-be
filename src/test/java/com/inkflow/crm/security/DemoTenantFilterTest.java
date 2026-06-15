package com.inkflow.crm.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.domain.enums.UserRole;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoTenantFilterTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private DemoTenantFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{\"error\":\"DEMO_READONLY\"}");
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldPassThroughWhenDemoTenantNotConfigured() throws Exception {
        ReflectionTestUtils.setField(filter, "demoTenantIdStr", "");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/clients");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldPassThroughForReadOnlyMethods() throws Exception {
        UUID demoTenantId = UUID.randomUUID();
        ReflectionTestUtils.setField(filter, "demoTenantIdStr", demoTenantId.toString());
        authenticate(demoTenantId);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clients");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldPassThroughWhenUserNotAuthenticated() throws Exception {
        ReflectionTestUtils.setField(filter, "demoTenantIdStr", UUID.randomUUID().toString());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/clients");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldAllowEmailPreviewForDemoTenant() throws Exception {
        UUID demoTenantId = UUID.randomUUID();
        ReflectionTestUtils.setField(filter, "demoTenantIdStr", demoTenantId.toString());
        authenticate(demoTenantId);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/emails/preview");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldBlockWriteRequestsForDemoTenant() throws Exception {
        UUID demoTenantId = UUID.randomUUID();
        ReflectionTestUtils.setField(filter, "demoTenantIdStr", demoTenantId.toString());
        authenticate(demoTenantId);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/clients");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(403, response.getStatus());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldAllowWriteRequestsForNonDemoTenant() throws Exception {
        UUID demoTenantId = UUID.randomUUID();
        UUID regularTenantId = UUID.randomUUID();
        ReflectionTestUtils.setField(filter, "demoTenantIdStr", demoTenantId.toString());
        authenticate(regularTenantId);

        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/clients/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldPassThroughWhenDemoTenantCheckFails() throws Exception {
        ReflectionTestUtils.setField(filter, "demoTenantIdStr", "not-a-uuid");
        authenticate(UUID.randomUUID());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/clients");
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
