package com.inkflow.crm.module.consumer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.module.catalog.dto.TattooStyleDto;
import com.inkflow.crm.module.catalog.service.TattooCatalogService;
import com.inkflow.crm.module.consumer.dto.GenerateRequest;
import com.inkflow.crm.module.consumer.dto.GenerateResponse;
import com.inkflow.crm.module.consumer.service.AIGeneratorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.inkflow.crm.support.SecurityTestSupport.consumerUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PublicEndpointSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AIGeneratorService aiGeneratorService;

    @MockBean
    private TattooCatalogService tattooCatalogService;

    @Test
    void catalogStyles_isPublic() throws Exception {
        when(tattooCatalogService.getStyles()).thenReturn(List.of(
                new TattooStyleDto(1L, "traditional", "Traditional", null, List.of())
        ));

        mockMvc.perform(get("/public/catalog/tattoos/styles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void aiGenerate_withoutConsumerAuth_returnsUnauthorized() throws Exception {
        GenerateRequest body = new GenerateRequest("dragon tattoo", "traditional", null, null, null, null);

        mockMvc.perform(post("/public/consumer/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void aiGenerate_withConsumerAuth_returnsOk() throws Exception {
        when(aiGeneratorService.generate(any())).thenReturn(GenerateResponse.success(List.of("https://example.com/img.png")));

        GenerateRequest body = new GenerateRequest("dragon tattoo", "traditional", null, null, null, null);

        mockMvc.perform(post("/public/consumer/generate")
                        .with(consumerUser(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.images[0]").value("https://example.com/img.png"));
    }
}
