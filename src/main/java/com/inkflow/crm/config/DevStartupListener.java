package com.inkflow.crm.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.lang.NonNull;

@Slf4j
public class DevStartupListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    static final String ENV_DATASOURCE_URL = "SPRING_DATASOURCE_URL";

    @Override
    public void onApplicationEvent(@NonNull ApplicationEnvironmentPreparedEvent event) {
        Environment env = event.getEnvironment();
        if (!env.acceptsProfiles("dev")) {
            return;
        }

        String datasourceUrl = env.getProperty(ENV_DATASOURCE_URL);
        if (datasourceUrl == null || datasourceUrl.isBlank()) {
            throw new IllegalStateException("""
                    Dev profile requires remote Supabase PostgreSQL.
                    Set SPRING_DATASOURCE_URL (and DB_USERNAME, DB_PASSWORD) in IntelliJ:
                    Run → Edit Configurations → Environment variables.
                    Local PostgreSQL is not used in dev.
                    """);
        }

        if (isLocalDatabase(datasourceUrl)) {
            throw new IllegalStateException(
                    "Dev profile must not use local PostgreSQL. "
                            + "Point SPRING_DATASOURCE_URL at Supabase (host *.supabase.co or pooler). "
                            + "Current value: " + sanitizeJdbcUrl(datasourceUrl)
            );
        }

        log.info("Dev startup: connecting to Supabase PostgreSQL at {}", sanitizeJdbcUrl(datasourceUrl));
    }

    static boolean isLocalDatabase(String url) {
        String lower = url.toLowerCase();
        return lower.contains("127.0.0.1")
                || lower.contains("localhost")
                || lower.contains("host.docker.internal");
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
