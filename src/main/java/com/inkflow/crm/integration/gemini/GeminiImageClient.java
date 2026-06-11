package com.inkflow.crm.integration.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.config.GeminiProperties;
import com.inkflow.crm.integration.gemini.dto.GeminiGenerateContentRequest;
import com.inkflow.crm.integration.gemini.dto.GeminiGenerateContentRequest.GeminiContent;
import com.inkflow.crm.integration.gemini.dto.GeminiGenerateContentRequest.GeminiGenerationConfig;
import com.inkflow.crm.integration.gemini.dto.GeminiGenerateContentRequest.GeminiPart;
import com.inkflow.crm.integration.gemini.dto.GeminiResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiImageClient {

    private static final List<String> IMAGE_MODALITIES = List.of("IMAGE", "TEXT");

    private final GeminiProperties geminiProperties;
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper mapper = new ObjectMapper();

    public String generateImage(GeminiGenerateContentRequest requestBody) throws Exception {
        String requestJson = mapper.writeValueAsString(requestBody);

        String responseJson = restClient.post()
                .uri(geminiProperties.imageGenerateUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestJson)
                .retrieve()
                .body(String.class);

        return extractImageDataUri(responseJson);
    }

    public GeminiGenerateContentRequest imageRequest(String prompt, double temperature) {
        return new GeminiGenerateContentRequest(
                List.of(new GeminiContent(List.of(GeminiPart.text(prompt)))),
                new GeminiGenerationConfig(IMAGE_MODALITIES, temperature)
        );
    }

    public GeminiGenerateContentRequest imageRequestWithParts(List<GeminiPart> parts, double temperature) {
        return new GeminiGenerateContentRequest(
                List.of(new GeminiContent(parts)),
                new GeminiGenerationConfig(IMAGE_MODALITIES, temperature)
        );
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
