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
    private Cors cors = new Cors();
    private Openapi openapi = new Openapi();

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
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>(List.of(
                "http://localhost:5173",
                "http://localhost:5174"
        ));
        private boolean allowCredentials = true;
    }
}
