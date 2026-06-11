package com.inkflow.crm.module.catalog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.integration.gemini.GeminiTextClient;
import com.inkflow.crm.module.catalog.dto.TattooAnalysisDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VisionService {

    private static final int MAX_RETRIES = 2;
    private static final long RETRY_BASE_DELAY_MS = 2000L;

    private final TattooTaggerService taggerService;
    private final GeminiTextClient geminiTextClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public TattooAnalysisDto analyze(String imageUrl) {
        Exception lastError = null;

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                return doAnalyze(imageUrl);
            } catch (Exception e) {
                lastError = e;
                log.warn("Vision analysis attempt {}/{} failed: {}", attempt + 1, MAX_RETRIES, e.getMessage());
                sleepBeforeRetry(attempt);
            }
        }

        log.error("Vision analysis failed after {} attempts for {}: {}",
                MAX_RETRIES, imageUrl, lastError != null ? lastError.getMessage() : "unknown");
        return null;
    }

    private TattooAnalysisDto doAnalyze(String imageUrl) throws Exception {
        String imageBase64 = downloadAsBase64(imageUrl);
        String prompt = buildAnalysisPrompt();
        Map<String, Object> request = geminiTextClient.textRequestWithImage(prompt, imageBase64);

        String rawText = geminiTextClient.generateText(request);
        return parseAnalysisJson(rawText);
    }

    private String buildAnalysisPrompt() {
        String availableTags = String.join(", ", taggerService.getAvailableTags());

        return "Analyze this tattoo image. Return a JSON object with exactly these fields:\n\n"
                + "1. \"description\" — a detailed description in Ukrainian (2-3 sentences) describing the tattoo style, "
                + "elements, body placement if visible, and artistic technique.\n\n"
                + "2. \"altDescription\" — a short one-line description in Ukrainian (up to 15 words).\n\n"
                + "3. \"tags\" — an array of applicable style tags from ONLY this list: ["
                + availableTags + "]. Pick 1-5 most relevant tags.\n\n"
                + "Return ONLY valid JSON, no markdown, no explanation.";
    }

    private TattooAnalysisDto parseAnalysisJson(String rawText) throws Exception {
        String json = rawText
                .replaceAll("^```json\\s*", "")
                .replaceAll("```\\s*$", "")
                .trim();
        return mapper.readValue(json, TattooAnalysisDto.class);
    }

    private String downloadAsBase64(String url) throws Exception {
        try (var stream = URI.create(url).toURL().openStream()) {
            return Base64.getEncoder().encodeToString(stream.readAllBytes());
        }
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(RETRY_BASE_DELAY_MS * (attempt + 1));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
