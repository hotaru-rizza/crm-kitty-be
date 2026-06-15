package com.inkflow.crm.module.email.service.sending;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.config.ResendConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ResendEmailClient {

    private final ResendConfig resendConfig;
    private final RestTemplate restTemplate = new RestTemplate();

    public void send(String to, String subject, String htmlBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(resendConfig.getApiKey());

        Map<String, Object> body = Map.of(
                "from", resendConfig.getFrom(),
                "to", List.of(to),
                "subject", subject,
                "html", htmlBody
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    resendConfig.getApiUrl(), HttpMethod.POST, request, String.class
            );
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Email sent to {} — subject: {}", to, subject);
                return;
            }
            log.error("Resend API error: {} — {}", response.getStatusCode(), response.getBody());
            throw new BusinessRuleException("Resend API returned " + response.getStatusCode());
        } catch (RestClientException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            throw new BusinessRuleException("Failed to send email: " + e.getMessage());
        }
    }
}
