package com.inkflow.crm.integration.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.config.GeminiProperties;
import com.inkflow.crm.integration.gemini.dto.GeminiGenerateContentRequest;
import com.inkflow.crm.integration.gemini.dto.GeminiGenerateContentRequest.GeminiContent;
import com.inkflow.crm.integration.gemini.dto.GeminiGenerateContentRequest.GeminiGenerationConfig;
import com.inkflow.crm.integration.gemini.dto.GeminiGenerateContentRequest.GeminiPart;
import com.inkflow.crm.integration.gemini.dto.GeminiResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GeminiTextClient {

    private static final List<String> TEXT_MODALITIES = List.of("TEXT");

    private final GeminiProperties geminiProperties;
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper mapper = new ObjectMapper();

    public String generateText(GeminiGenerateContentRequest requestBody) throws Exception {
        String requestJson = mapper.writeValueAsString(requestBody);

        String responseJson = restClient.post()
                .uri(geminiProperties.textGenerateUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestJson)
                .retrieve()
                .body(String.class);

        return extractText(responseJson);
    }

    public GeminiGenerateContentRequest textRequest(String prompt) {
        return new GeminiGenerateContentRequest(
                List.of(new GeminiContent(List.of(GeminiPart.text(prompt)))),
                new GeminiGenerationConfig(TEXT_MODALITIES, geminiProperties.getTemperature())
        );
    }

    public GeminiGenerateContentRequest textRequestWithImage(String prompt, String imageBase64) {
        return new GeminiGenerateContentRequest(
                List.of(new GeminiContent(List.of(
                        GeminiPart.text(prompt),
                        GeminiPart.jpegInline(imageBase64)
                ))),
                new GeminiGenerationConfig(TEXT_MODALITIES, geminiProperties.getTemperature())
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
