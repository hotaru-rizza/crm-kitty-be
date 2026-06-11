package com.inkflow.crm.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Google Gemini API settings. All keys map to {@code gemini.api.*} in application.yml.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gemini.api")
public class GeminiProperties {

    /** API key from Google AI Studio. */
    private String key;

    /** Text/vision model (portfolio analysis, tagging). */
    private String model = "gemini-2.5-flash";

    /** Image generation model (AI generator, try-on). */
    private String imageModel = "gemini-2.5-flash-image";

    /** Temperature for text/vision requests. */
    private double temperature = 0.3;

    /** Temperature for tattoo sketch / generator requests. */
    private double imageTemperature = 0.8;

    /** Temperature for virtual try-on compositing. */
    private double tryOnTemperature = 0.4;

    /** REST base URL for generateContent endpoints. */
    private String apiBase = "https://generativelanguage.googleapis.com/v1beta/models";

    /** Max side length when downscaling body images before Gemini upload. */
    private int maxImageSide = 1024;

    public String imageGenerateUrl() {
        return apiBase + "/" + imageModel + ":generateContent?key=" + key;
    }

    public String textGenerateUrl() {
        return apiBase + "/" + model + ":generateContent?key=" + key;
    }
}
