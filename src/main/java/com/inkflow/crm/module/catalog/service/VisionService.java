package com.inkflow.crm.module.catalog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.module.catalog.dto.GeminiResponseDto;
import com.inkflow.crm.module.catalog.dto.TattooAnalysisDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.*;

@Slf4j
@Service
public class VisionService {

    private static final String API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final int MAX_RETRIES = 2;
    private static final long RETRY_BASE_DELAY_MS = 2000L;

    private final TattooTaggerService taggerService;

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.model}")
    private String model;

    @Value("${gemini.api.temperature}")
    private double temperature;

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper mapper = new ObjectMapper();

    public VisionService(TattooTaggerService taggerService) {
        this.taggerService = taggerService;
    }

    public TattooAnalysisDto analyze(String imageUrl) {
        Exception lastError = null;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                return doAnalyze(imageUrl);
            } catch (Exception e) {
                lastError = e;
                log.warn("Vision analysis attempt {}/{} failed: {}", attempt + 1, MAX_RETRIES, e.getMessage());
                if (attempt + 1 < MAX_RETRIES) {
                    try { Thread.sleep(RETRY_BASE_DELAY_MS * (attempt + 1)); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        }
        log.error("Vision analysis failed after {} attempts for {}: {}", MAX_RETRIES, imageUrl, lastError != null ? lastError.getMessage() : "unknown");
        return null;
    }

    private TattooAnalysisDto doAnalyze(String imageUrl) throws Exception {
        String imageBase64 = downloadAsBase64(imageUrl);
        String requestJson = mapper.writeValueAsString(buildRequestBody(imageBase64));

        String apiUrl = API_BASE + model + ":generateContent?key=" + apiKey;

        String responseJson = restClient.post()
                .uri(apiUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestJson)
                .retrieve()
                .body(String.class);

        return parseResponse(responseJson);
    }

    private Map<String, Object> buildRequestBody(String imageBase64) {
        String availableTags = String.join(", ", taggerService.getAvailableTags());

        String prompt = "Analyze this tattoo image. Return a JSON object with exactly these fields:\n\n"
                + "1. \"description\" — a detailed description in Ukrainian (2-3 sentences) describing the tattoo style, "
                + "elements, body placement if visible, and artistic technique.\n\n"
                + "2. \"altDescription\" — a short one-line description in Ukrainian (up to 15 words).\n\n"
                + "3. \"tags\" — an array of applicable style tags from ONLY this list: ["
                + availableTags + "]. Pick 1-5 most relevant tags.\n\n"
                + "Return ONLY valid JSON, no markdown, no explanation.";

        Map<String, Object> imagePart = Map.of(
                "inline_data", Map.of("mime_type", "image/jpeg", "data", imageBase64)
        );
        Map<String, Object> textPart = Map.of("text", prompt);
        Map<String, Object> content = Map.of("parts", List.of(textPart, imagePart));
        Map<String, Object> generationConfig = Map.of(
                "response_modalities", List.of("TEXT"),
                "temperature", temperature
        );

        return Map.of(
                "contents", List.of(content),
                "generationConfig", generationConfig
        );
    }

    private TattooAnalysisDto parseResponse(String responseJson) throws Exception {
        GeminiResponseDto response = mapper.readValue(responseJson, GeminiResponseDto.class);

        if (response.error() != null) {
            throw new RuntimeException("Gemini error: " + response.error().message());
        }

        if (response.candidates() == null || response.candidates().isEmpty()) {
            throw new RuntimeException("No candidates in Gemini response");
        }

        List<GeminiResponseDto.Part> parts = response.candidates().getFirst().content().parts();
        for (GeminiResponseDto.Part part : parts) {
            if (part.text() != null) {
                String text = part.text().trim()
                        .replaceAll("^```json\\s*", "")
                        .replaceAll("```\\s*$", "")
                        .trim();
                return mapper.readValue(text, TattooAnalysisDto.class);
            }
        }
        throw new RuntimeException("No text in Gemini response");
    }

    private String downloadAsBase64(String url) throws Exception {
        try (var stream = URI.create(url).toURL().openStream()) {
            return Base64.getEncoder().encodeToString(stream.readAllBytes());
        }
    }
}
