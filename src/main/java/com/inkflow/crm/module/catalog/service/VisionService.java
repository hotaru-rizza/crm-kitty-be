package com.inkflow.crm.module.catalog.service;

import com.inkflow.crm.integration.gemini.GeminiVisionClient;
import com.inkflow.crm.module.catalog.dto.TattooAnalysisDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VisionService {

    private final TattooTaggerService taggerService;
    private final GeminiVisionClient geminiVisionClient;

    public TattooAnalysisDto analyze(String imageUrl) {
        return geminiVisionClient.analyzeImage(imageUrl, buildAnalysisPrompt(), TattooAnalysisDto.class);
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
}
