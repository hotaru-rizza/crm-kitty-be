package com.inkflow.crm.module.email.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.config.ResendConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResendEmailClientTest {

    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ResendConfig resendConfig;

    private ResendEmailClient resendEmailClient;

    @BeforeEach
    void setUp() {
        when(resendConfig.getApiKey()).thenReturn("re_test_key");
        when(resendConfig.getFrom()).thenReturn("Ink Studio <noreply@inkat.test>");

        resendEmailClient = new ResendEmailClient(resendConfig);
        ReflectionTestUtils.setField(resendEmailClient, "restTemplate", restTemplate);
    }

    @Test
    void shouldSendSuccessfullyWhenResendReturns2xx() {
        when(restTemplate.exchange(
                eq(RESEND_API_URL),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(new ResponseEntity<>("{\"id\":\"email_123\"}", HttpStatus.OK));

        assertDoesNotThrow(() -> resendEmailClient.send(
                "client@example.com",
                "Booking confirmed",
                "<p>Hello Anna</p>"
        ));

        ArgumentCaptor<HttpEntity<Map<String, Object>>> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq(RESEND_API_URL),
                eq(HttpMethod.POST),
                requestCaptor.capture(),
                eq(String.class)
        );

        Map<String, Object> body = requestCaptor.getValue().getBody();
        assertEquals("Ink Studio <noreply@inkat.test>", body.get("from"));
        assertEquals(List.of("client@example.com"), body.get("to"));
        assertEquals("Booking confirmed", body.get("subject"));
        assertEquals("<p>Hello Anna</p>", body.get("html"));
        assertTrue(requestCaptor.getValue().getHeaders().getFirst("Authorization").contains("re_test_key"));
    }

    @Test
    void shouldThrowBusinessRuleExceptionWhenResendReturnsNon2xx() {
        when(restTemplate.exchange(
                eq(RESEND_API_URL),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(new ResponseEntity<>("rate limited", HttpStatus.TOO_MANY_REQUESTS));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                resendEmailClient.send("client@example.com", "Subject", "<p>Body</p>")
        );

        assertTrue(ex.getMessage().contains("429"));
    }

    @Test
    void shouldThrowBusinessRuleExceptionWhenResendReturnsServerError() {
        when(restTemplate.exchange(
                eq(RESEND_API_URL),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(new ResponseEntity<>("internal error", HttpStatus.INTERNAL_SERVER_ERROR));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                resendEmailClient.send("client@example.com", "Subject", "<p>Body</p>")
        );

        assertTrue(ex.getMessage().contains("500"));
    }

    @Test
    void shouldThrowBusinessRuleExceptionWhenRestClientFails() {
        when(restTemplate.exchange(
                eq(RESEND_API_URL),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenThrow(new RestClientException("connection reset"));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                resendEmailClient.send("client@example.com", "Subject", "<p>Body</p>")
        );

        assertEquals("Failed to send email: connection reset", ex.getMessage());
    }
}
