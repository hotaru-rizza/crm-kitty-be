package com.inkflow.crm.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "resend")
@Getter
@Setter
public class ResendConfig {
    private String apiKey;
    private String apiUrl = "https://api.resend.com/emails";
    private String fromEmail;
    private String fromName;

    public String getFrom() {
        return fromName + " <" + fromEmail + ">";
    }
}
