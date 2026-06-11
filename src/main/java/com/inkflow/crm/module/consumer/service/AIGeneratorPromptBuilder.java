package com.inkflow.crm.module.consumer.service;

import com.inkflow.crm.config.GeminiProperties;
import com.inkflow.crm.module.consumer.dto.GenerateRequest;
import org.springframework.stereotype.Component;

@Component
public class AIGeneratorPromptBuilder {

    public String build(GenerateRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Generate a tattoo design. ");
        prompt.append("Design: ").append(request.prompt()).append(". ");

        appendStyle(prompt, request);
        appendColorMode(prompt, request);
        appendBackground(prompt, request);
        appendAspectRatio(prompt, request);

        prompt.append(" Output only the image, no text.");
        return prompt.toString();
    }

    private void appendStyle(StringBuilder prompt, GenerateRequest request) {
        if (request.style() == null || request.style().isBlank()) {
            return;
        }
        prompt.append("Style: ").append(request.style()).append(" tattoo style. ");
    }

    private void appendColorMode(StringBuilder prompt, GenerateRequest request) {
        if ("bw".equals(request.colorMode())) {
            prompt.append("Use only black and gray ink, no color. ");
            return;
        }
        prompt.append("Use full color, vibrant tattoo colors. ");
    }

    private void appendBackground(StringBuilder prompt, GenerateRequest request) {
        if (!"body".equals(request.background())) {
            prompt.append("Draw the design on a clean white paper background, like a tattoo flash sheet. ");
            prompt.append("No skin, no body — just the isolated tattoo artwork on white. ");
            return;
        }

        if (request.bodyImage() != null && !request.bodyImage().isBlank()) {
            prompt.append("Place the tattoo realistically on the body shown in the provided photo. ");
            prompt.append("Make it look like a real, healed tattoo embedded in the skin. ");
            prompt.append("The body photo must remain unchanged except for the added tattoo. ");
            return;
        }

        prompt.append("Show the tattoo realistically placed on human skin/body. ");
        prompt.append("Make it look like a real healed tattoo with skin texture visible through ink. ");
    }

    private void appendAspectRatio(StringBuilder prompt, GenerateRequest request) {
        String aspectDescription = switch (request.ratio() != null ? request.ratio() : "1:1") {
            case "9:16" -> "Portrait/vertical orientation.";
            case "16:9" -> "Landscape/horizontal orientation.";
            default -> "Square format.";
        };
        prompt.append(aspectDescription);
    }
}
