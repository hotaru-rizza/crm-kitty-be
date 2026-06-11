package com.inkflow.crm.integration.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.integration.gemini.dto.GeminiGenerateContentRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiVisionClient {

    private static final int MAX_RETRIES = 2;
    private static final long RETRY_BASE_DELAY_MS = 2000L;

    private final GeminiTextClient geminiTextClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public String analyzeImage(String imageUrl, String prompt) {
        Exception lastError = null;

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                return doAnalyze(imageUrl, prompt);
            } catch (Exception e) {
                lastError = e;
                log.warn("Gemini vision attempt {}/{} failed: {}", attempt + 1, MAX_RETRIES, e.getMessage());
                sleepBeforeRetry(attempt);
            }
        }

        log.error("Gemini vision failed after {} attempts for {}: {}",
                MAX_RETRIES, imageUrl, lastError != null ? lastError.getMessage() : "unknown");
        return null;
    }

    public <T> T analyzeImage(String imageUrl, String prompt, Class<T> responseType) {
        String rawText = analyzeImage(imageUrl, prompt);
        if (rawText == null) {
            return null;
        }
        try {
            return mapper.readValue(cleanJson(rawText), responseType);
        } catch (Exception e) {
            log.error("Failed to parse Gemini vision JSON for {}: {}", imageUrl, e.getMessage());
            return null;
        }
    }

    private String doAnalyze(String imageUrl, String prompt) throws Exception {
        String imageBase64 = downloadAsBase64(imageUrl);
        GeminiGenerateContentRequest request = geminiTextClient.textRequestWithImage(prompt, imageBase64);
        return geminiTextClient.generateText(request);
    }

    private String cleanJson(String rawText) {
        return rawText
                .replaceAll("^```json\\s*", "")
                .replaceAll("```\\s*$", "")
                .trim();
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
