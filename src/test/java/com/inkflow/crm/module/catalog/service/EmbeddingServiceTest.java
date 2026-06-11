package com.inkflow.crm.module.catalog.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EmbeddingServiceTest {

    private static final String MODEL_URL = "http://localhost/huggingface/embed";

    private EmbeddingService embeddingService;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();

        embeddingService = new EmbeddingService();
        ReflectionTestUtils.setField(embeddingService, "restClient", builder.build());
        ReflectionTestUtils.setField(embeddingService, "apiKey", "hf-test-key");
        ReflectionTestUtils.setField(embeddingService, "modelUrl", MODEL_URL);
    }

    @Test
    void toPgVector_formatsFloatArray() {
        assertEquals("[1.0,2.0,3.0]", embeddingService.toPgVector(new float[]{1f, 2f, 3f}));
    }

    @Test
    void toPgVector_handlesSingleValue() {
        assertEquals("[0.5]", embeddingService.toPgVector(new float[]{0.5f}));
    }

    @Test
    void toPgVector_handlesEmptyArray() {
        assertEquals("[]", embeddingService.toPgVector(new float[]{}));
    }

    @Test
    void shouldPrefixQueryWhenEmbedding() {
        mockServer.expect(requestTo(MODEL_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer hf-test-key"))
                .andExpect(content().json("{\"inputs\":\"query: wolf tattoo\"}"))
                .andRespond(withSuccess("[3,4]", MediaType.APPLICATION_JSON));

        float[] vector = embeddingService.embed("wolf tattoo");

        assertEquals(2, vector.length);
        assertTrue(Math.abs(magnitude(vector) - 1f) < 0.001f);
        mockServer.verify();
    }

    @Test
    void shouldPrefixPassageWhenEmbeddingPassage() {
        mockServer.expect(requestTo(MODEL_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"inputs\":\"passage: traditional rose sleeve\"}"))
                .andRespond(withSuccess("[3,4]", MediaType.APPLICATION_JSON));

        float[] vector = embeddingService.embedPassage("traditional rose sleeve");

        assertEquals(2, vector.length);
        assertTrue(Math.abs(magnitude(vector) - 1f) < 0.001f);
        mockServer.verify();
    }

    @Test
    void shouldNormalizeNonUnitVectorFromApi() {
        mockServer.expect(requestTo(MODEL_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("[3,4]", MediaType.APPLICATION_JSON));

        float[] vector = embeddingService.embed("any text");

        assertArrayEquals(new float[]{0.6f, 0.8f}, vector, 0.001f);
        mockServer.verify();
    }

    private static float magnitude(float[] vector) {
        float sum = 0f;
        for (float value : vector) {
            sum += value * value;
        }
        return (float) Math.sqrt(sum);
    }
}
