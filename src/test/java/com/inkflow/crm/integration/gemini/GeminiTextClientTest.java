package com.inkflow.crm.integration.gemini;

import com.inkflow.crm.config.GeminiProperties;
import com.inkflow.crm.integration.gemini.dto.GeminiGenerateContentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GeminiTextClientTest {

    private GeminiTextClient geminiTextClient;

    @BeforeEach
    void setUp() {
        GeminiProperties properties = new GeminiProperties();
        properties.setTemperature(0.4);
        geminiTextClient = new GeminiTextClient(properties);
    }

    @Test
    void textRequest_buildsSingleTextPart() {
        GeminiGenerateContentRequest request = geminiTextClient.textRequest("Analyze tattoo");

        assertNotNull(request.contents());
        assertEquals(1, request.contents().size());
        assertEquals("Analyze tattoo", request.contents().getFirst().parts().getFirst().text());
        assertEquals(0.4, request.generationConfig().temperature());
    }

    @Test
    void textRequestWithImage_includesInlineImagePart() {
        GeminiGenerateContentRequest request = geminiTextClient.textRequestWithImage("prompt", "abc123");

        assertEquals(2, request.contents().getFirst().parts().size());
        assertEquals("prompt", request.contents().getFirst().parts().getFirst().text());
        assertFalse(request.contents().getFirst().parts().get(1).inlineData().mimeType().isBlank());
    }
}
