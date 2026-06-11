package com.inkflow.crm.module.catalog.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Service
public class EmbeddingService {

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_BASE_DELAY_MS = 2000L;

    @Value("${huggingface.api.key}")
    private String apiKey;

    @Value("${huggingface.api.model-url}")
    private String modelUrl;

    private final RestClient restClient = RestClient.create();

    public float[] embedPassage(String text) {
        return query("passage: " + text);
    }

    public float[] embed(String text) {
        return query("query: " + text);
    }

    private float[] query(String prefixedText) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                float[] vector = restClient.post()
                        .uri(modelUrl)
                        .header("Authorization", "Bearer " + apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("inputs", prefixedText))
                        .retrieve()
                        .body(float[].class);

                if (vector == null || vector.length == 0) {
                    throw new IllegalStateException("Empty response from HuggingFace");
                }
                return normalize(vector);

            } catch (Exception e) {
                if (attempt == MAX_RETRIES) {
                    log.error("Embedding failed after {} attempts: {}", MAX_RETRIES, e.getMessage());
                    throw new RuntimeException("Failed to get embedding", e);
                }
                log.warn("Attempt {}/{} failed: {}. Retrying in {}s...",
                        attempt, MAX_RETRIES, e.getMessage(), attempt * 2);
                sleep(attempt * RETRY_BASE_DELAY_MS);
            }
        }
        throw new IllegalStateException("unreachable");
    }

    private float[] normalize(float[] v) {
        float norm = 0f;
        for (float x : v) norm += x * x;
        norm = (float) Math.sqrt(norm);
        if (norm == 0f) return v;
        for (int i = 0; i < v.length; i++) v[i] /= norm;
        return v;
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
    }

    public String toPgVector(float[] vector) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(",");
            }
            builder.append(vector[i]);
        }
        return builder.append("]").toString();
    }
}
