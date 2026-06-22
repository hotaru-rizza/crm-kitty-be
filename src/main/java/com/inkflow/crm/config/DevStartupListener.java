package com.inkflow.crm.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.lang.NonNull;

@Slf4j
public class DevStartupListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    @Override
    public void onApplicationEvent(@NonNull ApplicationEnvironmentPreparedEvent event) {
        Environment env = event.getEnvironment();
        if (!env.acceptsProfiles("dev")) {
            return;
        }

        String url = env.getProperty("spring.datasource.url", "unknown");
        log.info("Dev startup: waiting for PostgreSQL at {}", sanitizeJdbcUrl(url));
    }

    static String sanitizeJdbcUrl(String url) {
        int at = url.indexOf('@');
        if (at > 0) {
            int schemeEnd = url.indexOf("://");
            if (schemeEnd > 0) {
                return url.substring(0, schemeEnd + 3) + "***@" + url.substring(at + 1);
            }
        }
        return url;
    }
}
