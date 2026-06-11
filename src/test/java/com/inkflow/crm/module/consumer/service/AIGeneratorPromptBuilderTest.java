package com.inkflow.crm.module.consumer.service;

import com.inkflow.crm.module.consumer.dto.GenerateRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AIGeneratorPromptBuilderTest {

    private final AIGeneratorPromptBuilder promptBuilder = new AIGeneratorPromptBuilder();

    @Test
    void build_includesStyleAndBlackAndWhiteMode() {
        GenerateRequest request = new GenerateRequest(
                "dragon tattoo", "traditional", "bw", "plain", "1:1", null);

        String prompt = promptBuilder.build(request);

        assertTrue(prompt.contains("dragon tattoo"));
        assertTrue(prompt.contains("traditional"));
        assertTrue(prompt.contains("black and gray"));
        assertTrue(prompt.contains("Square format"));
    }

    @Test
    void build_usesBodyPlacementWhenBodyImageProvided() {
        GenerateRequest request = new GenerateRequest(
                "rose", null, null, "body", "9:16", "data:image/jpeg;base64,abc");

        String prompt = promptBuilder.build(request);

        assertTrue(prompt.contains("body shown in the provided photo"));
        assertTrue(prompt.contains("Portrait/vertical"));
    }

    @Test
    void build_usesFlashSheetBackgroundForNonBodyMode() {
        GenerateRequest request = new GenerateRequest(
                "wolf", "realism", "color", "plain", null, null);

        String prompt = promptBuilder.build(request);

        assertTrue(prompt.contains("white paper background"));
        assertTrue(prompt.contains("full color"));
    }
}
