package com.inkflow.crm.integration.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.config.GeminiProperties;
import com.inkflow.crm.module.catalog.dto.GeminiResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GeminiTextClient {

    private final GeminiProperties geminiProperties;
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper mapper = new ObjectMapper();

    public String generateText(Map<String, Object> requestBody) throws Exception {
        String requestJson = mapper.writeValueAsString(requestBody);

        String responseJson = restClient.post()
                .uri(geminiProperties.textGenerateUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestJson)
                .retrieve()
                .body(String.class);

        return extractText(responseJson);
    }

    public Map<String, Object> textRequest(String prompt) {
        return Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "response_modalities", List.of("TEXT"),
                        "temperature", geminiProperties.getTemperature()
                )
        );
    }

    public Map<String, Object> textRequestWithImage(String prompt, String imageBase64) {
        Map<String, Object> textPart = Map.of("text", prompt);
        Map<String, Object> imagePart = Map.of(
                "inline_data", Map.of("mime_type", "image/jpeg", "data", imageBase64)
        );
        return Map.of(
                "contents", List.of(Map.of("parts", List.of(textPart, imagePart))),
                "generationConfig", Map.of(
                        "response_modalities", List.of("TEXT"),
                        "temperature", geminiProperties.getTemperature()
                )
        );
    }

    private String extractText(String responseJson) throws Exception {
        GeminiResponseDto response = mapper.readValue(responseJson, GeminiResponseDto.class);

        if (response.error() != null) {
            throw new IllegalStateException("Gemini error: " + response.error().message());
        }
        if (response.candidates() == null || response.candidates().isEmpty()) {
            throw new IllegalStateException("No candidates in Gemini response");
        }

        List<GeminiResponseDto.Part> parts = response.candidates().getFirst().content().parts();
        for (GeminiResponseDto.Part part : parts) {
            if (part.text() != null && !part.text().isBlank()) {
                return part.text().trim();
            }
        }

        throw new IllegalStateException("No text in Gemini response");
    }
}
