package com.inkflow.crm.module.consumer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.module.consumer.dto.PlacementDto;
import com.inkflow.crm.module.consumer.dto.TryOnRequest;
import com.inkflow.crm.module.consumer.service.GeminiTattooService;
import com.inkflow.crm.support.IntegrationTest;
import com.inkflow.crm.support.SecurityTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.inkflow.crm.support.SecurityTestSupport.consumerUser;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class TryOnControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GeminiTattooService geminiTattooService;

    @AfterEach
    void tearDown() {
        SecurityTestSupport.clearAuthentication();
    }

    @Test
    void tryOn_withoutConsumerAuth_returnsUnauthorized() throws Exception {
        TryOnRequest body = sampleRequest();

        mockMvc.perform(post("/public/consumer/try-on")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void tryOn_withConsumerAuth_returnsResultUrl() throws Exception {
        when(geminiTattooService.generateTattooTryOn(
                anyString(), anyString(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn("data:image/png;base64,abc123");

        mockMvc.perform(post("/public/consumer/try-on")
                        .with(consumerUser(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.resultUrl").value("data:image/png;base64,abc123"))
                .andExpect(jsonPath("$.data.error").doesNotExist());

        ArgumentCaptor<Double> doubleCaptor = ArgumentCaptor.forClass(Double.class);
        verify(geminiTattooService).generateTattooTryOn(
                eq("data:image/png;base64,body"),
                eq("data:image/png;base64,sketch"),
                doubleCaptor.capture(),
                doubleCaptor.capture(),
                doubleCaptor.capture(),
                doubleCaptor.capture()
        );
        assertEquals(List.of(0.5, 0.4, 0.2, 15.0), doubleCaptor.getAllValues());
    }

    @Test
    void tryOn_whenServiceThrows_returnsOkWithError() throws Exception {
        when(geminiTattooService.generateTattooTryOn(
                anyString(), anyString(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new RuntimeException("Gemini unavailable"));

        mockMvc.perform(post("/public/consumer/try-on")
                        .with(consumerUser(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.error").value("Gemini unavailable"))
                .andExpect(jsonPath("$.data.resultUrl").doesNotExist());
    }

    @Test
    void tryOn_withMissingPlacement_returnsBadRequest() throws Exception {
        String json = """
                {
                  "bodyImage": "data:image/png;base64,body",
                  "sketchImage": "data:image/png;base64,sketch"
                }
                """;

        mockMvc.perform(post("/public/consumer/try-on")
                        .with(consumerUser(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.error.code").value("VALIDATION_ERROR"));
    }

    private TryOnRequest sampleRequest() {
        return new TryOnRequest(
                "data:image/png;base64,body",
                "data:image/png;base64,sketch",
                new PlacementDto(0.5, 0.4, 0.2, 15.0)
        );
    }
}
