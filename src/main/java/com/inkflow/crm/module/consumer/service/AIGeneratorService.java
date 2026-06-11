package com.inkflow.crm.module.consumer.service;

import com.inkflow.crm.config.GeminiProperties;
import com.inkflow.crm.integration.gemini.GeminiImageClient;
import com.inkflow.crm.integration.gemini.dto.GeminiGenerateContentRequest;
import com.inkflow.crm.integration.gemini.dto.GeminiGenerateContentRequest.GeminiPart;
import com.inkflow.crm.module.consumer.dto.GenerateRequest;
import com.inkflow.crm.module.consumer.dto.GenerateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIGeneratorService {

    private final GeminiProperties geminiProperties;
    private final GeminiImageClient geminiImageClient;
    private final GeminiImagePreprocessor imagePreprocessor;
    private final AIGeneratorPromptBuilder promptBuilder;

    public GenerateResponse generate(GenerateRequest request) {
        log.info("AI generate: style={}, color={}, bg={}, ratio={}",
                request.style(), request.colorMode(), request.background(), request.ratio());

        try {
            String prompt = promptBuilder.build(request);
            GeminiGenerateContentRequest apiRequest = buildApiRequest(request, prompt);
            String imageDataUri = geminiImageClient.generateImage(apiRequest);

            log.info("AI generate succeeded");
            return GenerateResponse.success(List.of(imageDataUri));

        } catch (Exception e) {
            log.error("AI generate failed: {}", e.getMessage(), e);
            return GenerateResponse.failure(e.getMessage());
        }
    }

    private GeminiGenerateContentRequest buildApiRequest(GenerateRequest request, String prompt) throws Exception {
        double temperature = geminiProperties.getImageTemperature();

        if (!"body".equals(request.background()) || request.bodyImage() == null || request.bodyImage().isBlank()) {
            return geminiImageClient.imageRequest(prompt, temperature);
        }

        String bodyBase64 = imagePreprocessor.toBase64Jpeg(request.bodyImage());
        List<GeminiPart> parts = new ArrayList<>();
        parts.add(GeminiPart.text(prompt));
        parts.add(GeminiPart.jpegInline(bodyBase64));
        return geminiImageClient.imageRequestWithParts(parts, temperature);
    }
}
