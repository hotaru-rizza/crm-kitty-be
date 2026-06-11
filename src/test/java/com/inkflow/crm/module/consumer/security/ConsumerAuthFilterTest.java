package com.inkflow.crm.module.consumer.security;

import com.auth0.jwk.JwkProvider;
import com.inkflow.crm.module.consumer.repository.ConsumerUserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ConsumerAuthFilterTest {

    private static final String JWT_ISSUER = "https://test.supabase.co/auth/v1";

    @Mock
    private ConsumerUserRepository consumerUserRepository;

    @Mock
    private JwkProvider jwkProvider;

    @Mock
    private FilterChain filterChain;

    private ConsumerAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ConsumerAuthFilter(consumerUserRepository);
        ReflectionTestUtils.setField(filter, "jwtIssuer", JWT_ISSUER);
        ReflectionTestUtils.setField(filter, "jwksUri", JWT_ISSUER + "/.well-known/jwks.json");
        ReflectionTestUtils.setField(filter, "jwkProvider", jwkProvider);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldPassThroughWhenAlreadyAuthenticated() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("existing", null)
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer some-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(consumerUserRepository, never()).findById(any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldPassThroughWhenNoJwtPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/public/consumer/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(consumerUserRepository, never()).findById(any());
        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldIgnoreAuthorizationHeaderWithoutBearerPrefix() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(consumerUserRepository, never()).findById(any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldContinueWithoutAuthenticationWhenJwtVerificationFails() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer not-a-jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(consumerUserRepository, never()).findById(any());
        verify(consumerUserRepository, never()).save(any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldIgnoreBearerTokenWithEmptyPayload() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(consumerUserRepository, never()).findById(any());
        verify(filterChain).doFilter(request, response);
    }
}
