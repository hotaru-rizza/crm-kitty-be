package com.inkflow.crm.module.monobank.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "monobank.acquiring")
public class MonobankConfig {
    private String token;
    private String apiUrl;
    private String webhookUrl;
    private String redirectUrl;
    private Integer invoiceValiditySeconds = 3600;
}
