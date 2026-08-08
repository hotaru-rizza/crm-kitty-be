package com.inkflow.crm.module.consumer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.support.IntegrationTest;
import com.inkflow.crm.module.catalog.dto.TattooStyleDto;
import com.inkflow.crm.module.catalog.service.TattooCatalogService;
import com.inkflow.crm.module.consumer.dto.ConsumerBookingRequest;
import com.inkflow.crm.module.consumer.dto.GenerateRequest;
import com.inkflow.crm.module.consumer.dto.GenerateResponse;
import com.inkflow.crm.module.consumer.dto.PlacementDto;
import com.inkflow.crm.module.consumer.dto.TryOnRequest;
import com.inkflow.crm.module.consumer.service.AIGeneratorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
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

@IntegrationTest
@AutoConfigureMockMvc
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
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].slug").value("traditional"))
                .andExpect(jsonPath("$.data[0].name").value("Traditional"));
    }

    @Test
    void consumerUsersMe_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/public/consumer/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void consumerBookingSubmit_withoutAuth_returnsUnauthorized() throws Exception {
        ConsumerBookingRequest body = new ConsumerBookingRequest(
                UUID.randomUUID(),
                "Alex Client",
                "next month",
                "medium",
                List.of("arm"),
                false,
                "Dragon sleeve",
                List.of(),
                "Kyiv",
                "telegram",
                "@alex",
                null,
                null
        );

        mockMvc.perform(post("/public/consumer/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void consumerBookingMy_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/public/consumer/requests/my"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void tryOn_withoutAuth_returnsUnauthorized() throws Exception {
        TryOnRequest body = new TryOnRequest(
                "data:image/png;base64,body",
                "data:image/png;base64,sketch",
                new PlacementDto(0.5, 0.5, 0.2, 0.0)
        );

        mockMvc.perform(post("/public/consumer/try-on")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.error.code").value("UNAUTHORIZED"));
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
        when(aiGeneratorService.generate(any())).thenReturn(
                new GenerateResponse(List.of("https://example.com/img.png"), null, null)
        );

        GenerateRequest body = new GenerateRequest("dragon tattoo", "traditional", null, null, null, null);

        mockMvc.perform(post("/public/consumer/generate")
                        .with(consumerUser(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.images").isArray())
                .andExpect(jsonPath("$.data.images[0]").value("https://example.com/img.png"));
    }
}
