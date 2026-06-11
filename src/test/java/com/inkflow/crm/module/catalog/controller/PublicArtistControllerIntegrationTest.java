package com.inkflow.crm.module.catalog.controller;

import com.inkflow.crm.module.catalog.dto.PublicArtistDto;
import com.inkflow.crm.module.catalog.service.PublicArtistService;
import com.inkflow.crm.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class PublicArtistControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PublicArtistService publicArtistService;

    @Test
    void getAll_isPublicAndReturnsArtistPayload() throws Exception {
        UUID artistId = UUID.randomUUID();
        when(publicArtistService.findAll(isNull(), isNull(), isNull()))
                .thenReturn(List.of(sampleArtist(artistId)));

        mockMvc.perform(get("/public/artists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(artistId.toString()))
                .andExpect(jsonPath("$.data[0].name").value("Alex Ink"))
                .andExpect(jsonPath("$.data[0].studioName").value("Ink Studio Kyiv"))
                .andExpect(jsonPath("$.data[0].isOpen").value(true))
                .andExpect(jsonPath("$.data[0].savesCount").value(42))
                .andExpect(jsonPath("$.data[0].styles[0]").value("traditional"));
    }

    @Test
    void getAll_passesQueryParamsToService() throws Exception {
        when(publicArtistService.findAll("Kyiv", "traditional", "dragon"))
                .thenReturn(List.of());

        mockMvc.perform(get("/public/artists")
                        .param("city", "Kyiv")
                        .param("style", "traditional")
                        .param("q", "dragon"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));

        verify(publicArtistService).findAll("Kyiv", "traditional", "dragon");
    }

    @Test
    void getById_returnsArtist() throws Exception {
        UUID artistId = UUID.randomUUID();
        when(publicArtistService.findById(artistId)).thenReturn(Optional.of(sampleArtist(artistId)));

        mockMvc.perform(get("/public/artists/{id}", artistId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(artistId.toString()))
                .andExpect(jsonPath("$.data.name").value("Alex Ink"))
                .andExpect(jsonPath("$.data.bio").value("Specializes in bold blackwork"))
                .andExpect(jsonPath("$.data.experience").value(8))
                .andExpect(jsonPath("$.data.hourlyRate").value(1500))
                .andExpect(jsonPath("$.data.studioAddress").value("Khreshchatyk 1, Kyiv"))
                .andExpect(jsonPath("$.data.isOpen").value(true))
                .andExpect(jsonPath("$.data.portfolio[0]").value("https://cdn.example.com/portfolio/1.jpg"))
                .andExpect(jsonPath("$.data.faq[0].question").value("Do you do cover-ups?"))
                .andExpect(jsonPath("$.data.reviews[0].rating").value(5));
    }

    @Test
    void getById_whenNotFound_returnsNotFound() throws Exception {
        UUID artistId = UUID.randomUUID();
        when(publicArtistService.findById(artistId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/public/artists/{id}", artistId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.error.code").value("STAFF_NOT_FOUND"));
    }

    private PublicArtistDto sampleArtist(UUID id) {
        return new PublicArtistDto(
                id,
                "Alex Ink",
                "https://cdn.example.com/avatar.png",
                "Specializes in bold blackwork",
                8,
                BigDecimal.valueOf(1500),
                "Ink Studio Kyiv",
                "Khreshchatyk 1, Kyiv",
                "https://cdn.example.com/studio.jpg",
                50.4501,
                30.5234,
                List.of("traditional", "blackwork"),
                List.of("realism portraits"),
                "https://instagram.com/alexink",
                true,
                42,
                List.of("https://cdn.example.com/portfolio/1.jpg"),
                List.of(new PublicArtistDto.ScheduleEntry("Mon", "10:00-18:00")),
                List.of(new PublicArtistDto.FaqEntry("Do you do cover-ups?", "Yes, after consultation")),
                List.of(new PublicArtistDto.ReviewEntry("r1", "Anna", 5, "Amazing work", "2026-01-15"))
        );
    }
}
