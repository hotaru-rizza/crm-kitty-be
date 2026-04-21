package com.inkflow.crm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "google.calendar")
public class GoogleCalendarProperties {
    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String frontendRedirect;

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }
}
