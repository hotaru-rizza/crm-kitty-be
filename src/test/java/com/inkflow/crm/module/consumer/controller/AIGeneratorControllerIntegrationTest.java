package com.inkflow.crm.module.consumer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.module.consumer.dto.GenerateRequest;
import com.inkflow.crm.module.consumer.dto.GenerateResponse;
import com.inkflow.crm.module.consumer.service.AIGeneratorService;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class AIGeneratorControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AIGeneratorService aiGeneratorService;

    @AfterEach
    void tearDown() {
        SecurityTestSupport.clearAuthentication();
    }

    @Test
    void generate_withoutConsumerAuth_returnsUnauthorized() throws Exception {
        GenerateRequest body = new GenerateRequest("dragon tattoo", "traditional", null, null, null, null);

        mockMvc.perform(post("/public/consumer/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void generate_withConsumerAuth_returnsImages() throws Exception {
        when(aiGeneratorService.generate(any())).thenReturn(
                GenerateResponse.success(List.of("https://cdn.example.com/gen1.png", "https://cdn.example.com/gen2.png"))
        );

        GenerateRequest body = new GenerateRequest("dragon tattoo", "traditional", "color", "white", "1:1", null);

        mockMvc.perform(post("/public/consumer/generate")
                        .with(consumerUser(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.images[0]").value("https://cdn.example.com/gen1.png"))
                .andExpect(jsonPath("$.data.images[1]").value("https://cdn.example.com/gen2.png"));

        ArgumentCaptor<GenerateRequest> captor = ArgumentCaptor.forClass(GenerateRequest.class);
        verify(aiGeneratorService).generate(captor.capture());
        assertEquals("dragon tattoo", captor.getValue().prompt());
        assertEquals("traditional", captor.getValue().style());
        assertEquals("color", captor.getValue().colorMode());
    }

    @Test
    void generate_withBlankPrompt_returnsBadRequest() throws Exception {
        GenerateRequest body = new GenerateRequest("", "traditional", null, null, null, null);

        mockMvc.perform(post("/public/consumer/generate")
                        .with(consumerUser(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void generate_whenServiceReturnsFailure_returnsOkWithError() throws Exception {
        when(aiGeneratorService.generate(any())).thenReturn(GenerateResponse.failure("Rate limit exceeded"));

        GenerateRequest body = new GenerateRequest("dragon tattoo", "traditional", null, null, null, null);

        mockMvc.perform(post("/public/consumer/generate")
                        .with(consumerUser(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.error").value("Rate limit exceeded"))
                .andExpect(jsonPath("$.data.images").isEmpty());
    }
}
