package com.inkflow.crm.module.catalog.service;

import com.inkflow.crm.integration.gemini.GeminiVisionClient;
import com.inkflow.crm.module.catalog.dto.TattooAnalysisDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VisionServiceTest {

    @Mock
    private TattooTaggerService taggerService;

    @Mock
    private GeminiVisionClient geminiVisionClient;

    @InjectMocks
    private VisionService visionService;

    @Test
    void analyze_buildsPromptFromAvailableTags() {
        when(taggerService.getAvailableTags()).thenReturn(Set.of("traditional", "realism"));
        when(geminiVisionClient.analyzeImage(
                eq("https://cdn.example.com/t.jpg"),
                org.mockito.ArgumentMatchers.anyString(),
                eq(TattooAnalysisDto.class)
        )).thenReturn(new TattooAnalysisDto("desc", "alt", java.util.List.of("traditional")));

        TattooAnalysisDto result = visionService.analyze("https://cdn.example.com/t.jpg");

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(geminiVisionClient).analyzeImage(
                eq("https://cdn.example.com/t.jpg"),
                promptCaptor.capture(),
                eq(TattooAnalysisDto.class)
        );

        String prompt = promptCaptor.getValue();
        assertTrue(prompt.contains("traditional"));
        assertTrue(prompt.contains("realism"));
        assertEquals("desc", result.description());
    }

    @Test
    void shouldReturnNullWhenVisionClientReturnsNull() {
        when(taggerService.getAvailableTags()).thenReturn(Set.of("traditional"));
        when(geminiVisionClient.analyzeImage(
                eq("https://cdn.example.com/t.jpg"),
                org.mockito.ArgumentMatchers.anyString(),
                eq(TattooAnalysisDto.class)
        )).thenReturn(null);

        TattooAnalysisDto result = visionService.analyze("https://cdn.example.com/t.jpg");

        assertNull(result);
        verify(geminiVisionClient).analyzeImage(
                eq("https://cdn.example.com/t.jpg"),
                org.mockito.ArgumentMatchers.anyString(),
                eq(TattooAnalysisDto.class)
        );
    }
}
