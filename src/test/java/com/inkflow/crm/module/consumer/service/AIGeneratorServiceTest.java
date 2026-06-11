package com.inkflow.crm.module.consumer.service;

import com.inkflow.crm.config.GeminiProperties;
import com.inkflow.crm.integration.gemini.GeminiImageClient;
import com.inkflow.crm.integration.gemini.dto.GeminiGenerateContentRequest;
import com.inkflow.crm.module.consumer.dto.GenerateRequest;
import com.inkflow.crm.module.consumer.dto.GenerateResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AIGeneratorServiceTest {

    @Mock
    private GeminiProperties geminiProperties;

    @Mock
    private GeminiImageClient geminiImageClient;

    @Mock
    private GeminiImagePreprocessor imagePreprocessor;

    @Mock
    private AIGeneratorPromptBuilder promptBuilder;

    @InjectMocks
    private AIGeneratorService aiGeneratorService;

    @Test
    void shouldReturnImageWhenPlainBackgroundGenerationSucceeds() throws Exception {
        GenerateRequest request = new GenerateRequest("dragon tattoo", "traditional", null, "plain", null, null);

        when(promptBuilder.build(request)).thenReturn("prompt text");
        when(geminiProperties.getImageTemperature()).thenReturn(0.6);
        when(geminiImageClient.imageRequest("prompt text", 0.6))
                .thenReturn(new GeminiGenerateContentRequest(List.of(), null));
        when(geminiImageClient.generateImage(any())).thenReturn("data:image/png;base64,abc");

        GenerateResponse response = aiGeneratorService.generate(request);

        assertNull(response.error());
        assertEquals(List.of("data:image/png;base64,abc"), response.images());
        verify(promptBuilder).build(request);
        verify(geminiImageClient).imageRequest("prompt text", 0.6);
        verify(geminiImageClient, never()).imageRequestWithParts(anyList(), anyDouble());
        verify(imagePreprocessor, never()).toBase64Jpeg(anyString());
    }

    @Test
    void shouldUseMultiPartRequestWhenBodyImageProvided() throws Exception {
        GenerateRequest request = new GenerateRequest(
                "rose", "traditional", null, "body", "1:1", "data:image/jpeg;base64,body");

        when(promptBuilder.build(request)).thenReturn("body prompt");
        when(geminiProperties.getImageTemperature()).thenReturn(0.5);
        when(imagePreprocessor.toBase64Jpeg("data:image/jpeg;base64,body")).thenReturn("body-b64");
        when(geminiImageClient.imageRequestWithParts(anyList(), eq(0.5)))
                .thenReturn(new GeminiGenerateContentRequest(List.of(), null));
        when(geminiImageClient.generateImage(any())).thenReturn("data:image/png;base64,body-result");

        GenerateResponse response = aiGeneratorService.generate(request);

        assertNull(response.error());
        assertEquals(List.of("data:image/png;base64,body-result"), response.images());
        verify(imagePreprocessor).toBase64Jpeg("data:image/jpeg;base64,body");
        verify(geminiImageClient).imageRequestWithParts(anyList(), eq(0.5));
        verify(geminiImageClient, never()).imageRequest(anyString(), anyDouble());
    }

    @Test
    void shouldReturnFailureWhenClientThrows() throws Exception {
        GenerateRequest request = new GenerateRequest("dragon tattoo", "traditional", null, "plain", null, null);

        when(promptBuilder.build(request)).thenReturn("prompt text");
        when(geminiProperties.getImageTemperature()).thenReturn(0.6);
        when(geminiImageClient.imageRequest(anyString(), anyDouble()))
                .thenReturn(new GeminiGenerateContentRequest(List.of(), null));
        when(geminiImageClient.generateImage(any())).thenThrow(new RuntimeException("gemini down"));

        GenerateResponse response = aiGeneratorService.generate(request);

        assertEquals("gemini down", response.error());
        assertEquals(List.of(), response.images());
        verify(promptBuilder).build(request);
    }

    @Test
    void shouldReturnFailureWhenPreprocessorThrows() throws Exception {
        GenerateRequest request = new GenerateRequest(
                "rose", null, null, "body", null, "data:image/jpeg;base64,bad");

        when(promptBuilder.build(request)).thenReturn("body prompt");
        when(imagePreprocessor.toBase64Jpeg(anyString()))
                .thenThrow(new IllegalArgumentException("Cannot decode image"));

        GenerateResponse response = aiGeneratorService.generate(request);

        assertEquals("Cannot decode image", response.error());
        assertEquals(List.of(), response.images());
        verify(geminiImageClient, never()).generateImage(any());
    }
}
