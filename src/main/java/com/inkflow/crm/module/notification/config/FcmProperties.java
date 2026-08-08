package com.inkflow.crm.module.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "fcm")
public class FcmProperties {

    /**
     * When false, push sending is a no-op even if credentials exist.
     */
    private boolean enabled = false;

    /**
     * Full Firebase service-account JSON (preferred for cloud env vars).
     */
    private String credentialsJson = "";

    /**
     * Path to a service-account JSON file (handy for local dev).
     */
    private String credentialsPath = "";

    public boolean hasCredentials() {
        return StringUtils.hasText(credentialsJson) || StringUtils.hasText(credentialsPath);
    }
}
