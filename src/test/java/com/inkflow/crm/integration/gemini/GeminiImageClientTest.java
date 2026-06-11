package com.inkflow.crm.integration.gemini;

import com.inkflow.crm.config.GeminiProperties;
import com.inkflow.crm.integration.gemini.dto.GeminiGenerateContentRequest;
import com.inkflow.crm.integration.gemini.dto.GeminiGenerateContentRequest.GeminiPart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GeminiImageClientTest {

    private GeminiImageClient geminiImageClient;

    @BeforeEach
    void setUp() {
        GeminiProperties properties = new GeminiProperties();
        geminiImageClient = new GeminiImageClient(properties);
    }

    @Test
    void shouldBuildSingleTextPartImageRequest() {
        GeminiGenerateContentRequest request = geminiImageClient.imageRequest("dragon tattoo", 0.7);

        assertNotNull(request.contents());
        assertEquals(1, request.contents().size());
        assertEquals("dragon tattoo", request.contents().getFirst().parts().getFirst().text());
        assertEquals(List.of("IMAGE", "TEXT"), request.generationConfig().responseModalities());
        assertEquals(0.7, request.generationConfig().temperature());
    }

    @Test
    void shouldBuildMultiPartImageRequestWithCustomTemperature() {
        List<GeminiPart> parts = List.of(
                GeminiPart.text("try-on prompt"),
                GeminiPart.jpegInline("body-b64"),
                GeminiPart.jpegInline("sketch-b64")
        );

        GeminiGenerateContentRequest request = geminiImageClient.imageRequestWithParts(parts, 0.4);

        assertEquals(3, request.contents().getFirst().parts().size());
        assertEquals("try-on prompt", request.contents().getFirst().parts().getFirst().text());
        assertEquals(0.4, request.generationConfig().temperature());
    }
}
