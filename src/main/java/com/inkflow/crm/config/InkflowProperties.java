package com.inkflow.crm.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "inkflow")
public class InkflowProperties {

    private String appName = "CRM";
    private String defaultTimezone = "Europe/Kyiv";
    private String defaultLanguage = "uk";
    private String defaultStartPage = "/calendar";
    private String defaultAccentTheme = "orange";
    private String defaultColorScheme = "dark";
    private String frontendUrl = "http://localhost:5173";
    private Invite invite = new Invite();
    private Cors cors = new Cors();
    private Openapi openapi = new Openapi();
    private Email email = new Email();
    private Audit audit = new Audit();
    private Appointments appointments = new Appointments();

    public ZoneId defaultZoneId() {
        return ZoneId.of(defaultTimezone);
    }

    @Getter
    @Setter
    public static class Openapi {
        private boolean enabled = false;
    }

    @Getter
    @Setter
    public static class Audit {
        private boolean retentionEnabled = true;
        private int retentionDays = 730;
        private String retentionCron = "0 0 3 * * *";
        private String systemActorName = "system@inkflow";
    }

    @Getter
    @Setter
    public static class Email {
        private Scheduler scheduler = new Scheduler();
        private Outbox outbox = new Outbox();
    }

    @Getter
    @Setter
    public static class Scheduler {
        private long fixedRateMs = 900_000;
        private int catchUpMaxHours = 48;
    }

    @Getter
    @Setter
    public static class Outbox {
        private long fixedRateMs = 30_000;
        private int batchSize = 20;
        private int maxAttempts = 5;
        private long baseBackoffMs = 60_000;

        public long backoffMsForAttempt(int attempt) {
            return baseBackoffMs * (1L << Math.min(attempt - 1, 10));
        }
    }

    @Getter
    @Setter
    public static class Invite {
        private int ttlDays = 7;
        private boolean cleanupEnabled = true;
        private int cleanupRetentionDaysAfterExpiry = 30;
    }

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>(List.of(
                "http://localhost:5173",
                "http://localhost:5174"
        ));
        private boolean allowCredentials = true;
    }

    @Getter
    @Setter
    public static class Appointments {
        private int defaultReservationDurationMinutes = 60;
    }
}
