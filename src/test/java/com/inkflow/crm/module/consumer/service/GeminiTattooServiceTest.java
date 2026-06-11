package com.inkflow.crm.module.consumer.service;

import com.inkflow.crm.config.GeminiProperties;
import com.inkflow.crm.integration.gemini.GeminiImageClient;
import com.inkflow.crm.integration.gemini.dto.GeminiGenerateContentRequest;
import com.inkflow.crm.integration.gemini.dto.GeminiGenerateContentRequest.GeminiPart;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeminiTattooServiceTest {

    @Mock
    private GeminiProperties geminiProperties;

    @Mock
    private GeminiImageClient geminiImageClient;

    @Mock
    private GeminiImagePreprocessor imagePreprocessor;

    @InjectMocks
    private GeminiTattooService geminiTattooService;

    @Test
    void shouldBuildSketchPromptWithBodyPartContext() throws Exception {
        when(geminiProperties.getImageTemperature()).thenReturn(0.55);
        when(geminiImageClient.imageRequest(anyString(), eq(0.55)))
                .thenReturn(new GeminiGenerateContentRequest(List.of(), null));
        when(geminiImageClient.generateImage(any())).thenReturn("data:image/png;base64,sketch");

        String result = geminiTattooService.generateSketch("wolf", "forearm");

        assertEquals("data:image/png;base64,sketch", result);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(geminiImageClient).imageRequest(promptCaptor.capture(), eq(0.55));
        String prompt = promptCaptor.getValue();
        assertTrue(prompt.contains("wolf"));
        assertTrue(prompt.contains("forearm"));
        assertTrue(prompt.contains("Output ONLY the tattoo design sketch"));
    }

    @Test
    void shouldBuildMultiPartTryOnRequestWithPositionDescription() throws Exception {
        when(imagePreprocessor.toBase64Jpeg("body.jpg")).thenReturn("body-b64");
        when(imagePreprocessor.toBase64Jpeg("sketch.jpg")).thenReturn("sketch-b64");
        when(geminiProperties.getTryOnTemperature()).thenReturn(0.4);
        when(geminiImageClient.imageRequestWithParts(anyList(), eq(0.4)))
                .thenReturn(new GeminiGenerateContentRequest(List.of(), null));
        when(geminiImageClient.generateImage(any())).thenReturn("data:image/png;base64,tryon");

        String result = geminiTattooService.generateTattooTryOn(
                "body.jpg", "sketch.jpg", 0.5, 0.5, 0.2, 15.0);

        assertEquals("data:image/png;base64,tryon", result);

        verify(imagePreprocessor).toBase64Jpeg("body.jpg");
        verify(imagePreprocessor).toBase64Jpeg("sketch.jpg");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GeminiPart>> partsCaptor = ArgumentCaptor.forClass(List.class);
        verify(geminiImageClient).imageRequestWithParts(partsCaptor.capture(), eq(0.4));
        List<GeminiPart> parts = partsCaptor.getValue();
        assertEquals(3, parts.size());
        assertEquals("body-b64", parts.get(1).inlineData().data());
        assertEquals("sketch-b64", parts.get(2).inlineData().data());
    }

    @Test
    void shouldDescribeUpperLeftSmallPositionWithoutRotation() throws Exception {
        when(imagePreprocessor.toBase64Jpeg(anyString())).thenReturn("b64");
        when(geminiProperties.getTryOnTemperature()).thenReturn(0.4);
        when(geminiImageClient.imageRequestWithParts(anyList(), anyDouble()))
                .thenReturn(new GeminiGenerateContentRequest(List.of(), null));
        when(geminiImageClient.generateImage(any())).thenReturn("data:image/png;base64,tryon");

        geminiTattooService.generateTattooTryOn("body.jpg", "sketch.jpg", 0.1, 0.1, 0.1, 0.0);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GeminiPart>> partsCaptor = ArgumentCaptor.forClass(List.class);
        verify(geminiImageClient).imageRequestWithParts(partsCaptor.capture(), anyDouble());
        String promptText = partsCaptor.getValue().getFirst().text();
        assertTrue(promptText.contains("upper-left"));
        assertTrue(promptText.contains("small"));
    }
}
