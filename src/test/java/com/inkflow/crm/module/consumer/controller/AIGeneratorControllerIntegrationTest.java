package com.inkflow.crm.module.consumer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.module.consumer.dto.GenerateRequest;
import com.inkflow.crm.module.consumer.dto.GenerateResponse;
import com.inkflow.crm.module.consumer.entity.ConsumerUser;
import com.inkflow.crm.module.consumer.repository.ConsumerUserRepository;
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

    @Autowired
    private ConsumerUserRepository consumerUserRepository;

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
    void generate_withConsumerAuth_returnsImagesAndDecrementsTokens() throws Exception {
        UUID consumerId = UUID.randomUUID();
        consumerUserRepository.save(new ConsumerUser(consumerId, "consumer-" + consumerId + "@test.com", "User"));

        when(aiGeneratorService.generate(any())).thenReturn(
                new GenerateResponse(List.of("https://cdn.example.com/gen1.png"), null, null)
        );

        GenerateRequest body = new GenerateRequest("dragon tattoo", "traditional", "color", "white", "1:1", null);

        mockMvc.perform(post("/public/consumer/generate")
                        .with(consumerUser(consumerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.images[0]").value("https://cdn.example.com/gen1.png"))
                .andExpect(jsonPath("$.data.remainingTokens").value(4));

        ArgumentCaptor<GenerateRequest> captor = ArgumentCaptor.forClass(GenerateRequest.class);
        verify(aiGeneratorService).generate(captor.capture());
        assertEquals("dragon tattoo", captor.getValue().prompt());
        assertEquals("traditional", captor.getValue().style());
        assertEquals("color", captor.getValue().colorMode());
    }

    @Test
    void generate_withNoTokens_returnsPaymentRequired() throws Exception {
        UUID consumerId = UUID.randomUUID();
        ConsumerUser user = new ConsumerUser(consumerId, "consumer-" + consumerId + "@test.com", "User");
        user.setAiTokens(0);
        consumerUserRepository.save(user);

        GenerateRequest body = new GenerateRequest("dragon tattoo", "traditional", null, null, null, null);

        mockMvc.perform(post("/public/consumer/generate")
                        .with(consumerUser(consumerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error.error.code").value("INSUFFICIENT_TOKENS"));
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
    void generate_whenServiceReturnsFailure_doesNotChargeTokens() throws Exception {
        UUID consumerId = UUID.randomUUID();
        consumerUserRepository.save(new ConsumerUser(consumerId, "consumer-" + consumerId + "@test.com", "User"));

        when(aiGeneratorService.generate(any())).thenReturn(GenerateResponse.failure("Rate limit exceeded"));

        GenerateRequest body = new GenerateRequest("dragon tattoo", "traditional", null, null, null, null);

        mockMvc.perform(post("/public/consumer/generate")
                        .with(consumerUser(consumerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.error").value("Rate limit exceeded"))
                .andExpect(jsonPath("$.data.images").isEmpty())
                .andExpect(jsonPath("$.data.remainingTokens").doesNotExist());

        assertEquals(5, consumerUserRepository.findById(consumerId).orElseThrow().getAiTokens());
    }
}
