package com.inkflow.crm.integration.gemini;

import com.inkflow.crm.integration.gemini.dto.GeminiGenerateContentRequest;
import com.inkflow.crm.module.catalog.dto.TattooAnalysisDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeminiVisionClientTest {

    @Mock
    private GeminiTextClient geminiTextClient;

    @InjectMocks
    private GeminiVisionClient geminiVisionClient;

    @Test
    void analyzeImage_parsesJsonResponse(@TempDir Path tempDir) throws Exception {
        Path image = tempDir.resolve("sample.jpg");
        Files.write(image, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

        when(geminiTextClient.textRequestWithImage(any(), any()))
                .thenReturn(new GeminiGenerateContentRequest(List.of(), null));
        when(geminiTextClient.generateText(any())).thenReturn("""
                {"description":"Test","altDescription":"Short","tags":["traditional"]}
                """);

        TattooAnalysisDto result = geminiVisionClient.analyzeImage(
                image.toUri().toString(),
                "prompt",
                TattooAnalysisDto.class
        );

        assertNotNull(result);
        assertEquals("Test", result.description());
        assertEquals(List.of("traditional"), result.tags());
        verify(geminiTextClient).textRequestWithImage(org.mockito.ArgumentMatchers.eq("prompt"), any());
        verify(geminiTextClient).generateText(any());
    }

    @Test
    void shouldStripMarkdownFencesBeforeParsingJson(@TempDir Path tempDir) throws Exception {
        Path image = tempDir.resolve("sample.jpg");
        Files.write(image, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

        when(geminiTextClient.textRequestWithImage(any(), any()))
                .thenReturn(new GeminiGenerateContentRequest(List.of(), null));
        when(geminiTextClient.generateText(any())).thenReturn("""
                ```json
                {"description":"Wrapped","altDescription":"Short","tags":["realism"]}
                ```
                """);

        TattooAnalysisDto result = geminiVisionClient.analyzeImage(
                image.toUri().toString(),
                "prompt",
                TattooAnalysisDto.class
        );

        assertNotNull(result);
        assertEquals("Wrapped", result.description());
        assertEquals(List.of("realism"), result.tags());
    }

    @Test
    void shouldReturnNullWhenJsonParseFails(@TempDir Path tempDir) throws Exception {
        Path image = tempDir.resolve("sample.jpg");
        Files.write(image, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

        when(geminiTextClient.textRequestWithImage(any(), any()))
                .thenReturn(new GeminiGenerateContentRequest(List.of(), null));
        when(geminiTextClient.generateText(any())).thenReturn("not-json");

        TattooAnalysisDto result = geminiVisionClient.analyzeImage(
                image.toUri().toString(),
                "prompt",
                TattooAnalysisDto.class
        );

        assertNull(result);
    }

    @Test
    void analyzeImage_returnsNullWhenGeminiFails(@TempDir Path tempDir) throws Exception {
        Path image = tempDir.resolve("sample.jpg");
        Files.write(image, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

        when(geminiTextClient.textRequestWithImage(any(), any()))
                .thenReturn(new GeminiGenerateContentRequest(List.of(), null));
        when(geminiTextClient.generateText(any())).thenThrow(new IllegalStateException("Gemini down"));

        String raw = geminiVisionClient.analyzeImage(image.toUri().toString(), "prompt");

        assertNull(raw);
    }
}
