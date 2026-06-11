package com.inkflow.crm.module.consumer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.config.GeminiProperties;
import com.inkflow.crm.module.catalog.dto.GeminiResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiImageClient {

    private final GeminiProperties geminiProperties;
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper mapper = new ObjectMapper();

    public String generateImage(Map<String, Object> requestBody) throws Exception {
        String requestJson = mapper.writeValueAsString(requestBody);

        String responseJson = restClient.post()
                .uri(geminiProperties.imageGenerateUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestJson)
                .retrieve()
                .body(String.class);

        return extractImageDataUri(responseJson);
    }

    public Map<String, Object> imageRequest(String prompt, double temperature) {
        return Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "response_modalities", List.of("IMAGE", "TEXT"),
                        "temperature", temperature
                )
        );
    }

    public Map<String, Object> imageRequestWithParts(List<Map<String, Object>> parts, double temperature) {
        return Map.of(
                "contents", List.of(Map.of("parts", parts)),
                "generationConfig", Map.of(
                        "response_modalities", List.of("IMAGE", "TEXT"),
                        "temperature", temperature
                )
        );
    }

    public Map<String, Object> textPart(String text) {
        return Map.of("text", text);
    }

    public Map<String, Object> jpegInlinePart(String base64Data) {
        return Map.of("inline_data", Map.of("mime_type", "image/jpeg", "data", base64Data));
    }

    private String extractImageDataUri(String responseJson) throws Exception {
        GeminiResponseDto response = mapper.readValue(responseJson, GeminiResponseDto.class);

        if (response.error() != null) {
            throw new IllegalStateException("Gemini API error: " + response.error().message());
        }
        if (response.candidates() == null || response.candidates().isEmpty()) {
            throw new IllegalStateException("No candidates in Gemini response");
        }

        List<GeminiResponseDto.Part> parts = response.candidates().getFirst().content().parts();
        for (GeminiResponseDto.Part part : parts) {
            GeminiResponseDto.InlineData inlineData = part.resolvedInlineData();
            if (inlineData != null && inlineData.data() != null) {
                return "data:" + inlineData.resolvedMimeType() + ";base64," + inlineData.data();
            }
        }

        throw new IllegalStateException("No image in Gemini response");
    }
}
