package com.inkflow.crm.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gemini.api")
public class GeminiProperties {

    private String key;
    private String model = "gemini-2.5-flash";
    private String imageModel = "gemini-2.5-flash-image";
    private double temperature = 0.3;
    private double imageTemperature = 0.8;
    private double tryOnTemperature = 0.4;
    private String apiBase = "https://generativelanguage.googleapis.com/v1beta/models";
    private int maxImageSide = 1024;

    public String imageGenerateUrl() {
        return apiBase + "/" + imageModel + ":generateContent?key=" + key;
    }

    public String textGenerateUrl() {
        return apiBase + "/" + model + ":generateContent?key=" + key;
    }
}
