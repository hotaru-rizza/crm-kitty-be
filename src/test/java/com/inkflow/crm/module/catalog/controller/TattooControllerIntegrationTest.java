package com.inkflow.crm.module.catalog.controller;

import com.inkflow.crm.module.catalog.dto.TattooDto;
import com.inkflow.crm.module.catalog.dto.TattooStyleDto;
import com.inkflow.crm.module.catalog.service.TattooCatalogService;
import com.inkflow.crm.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class TattooControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TattooCatalogService tattooCatalogService;

    @Test
    void getFeed_isPublicAndReturnsPagedPayload() throws Exception {
        UUID staffId = UUID.randomUUID();
        when(tattooCatalogService.getFeed(eq(null), eq(null), eq(null), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(sampleTattoo(1L, staffId)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/public/catalog/tattoos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].staffId").value(staffId.toString()))
                .andExpect(jsonPath("$.data[0].status").value("active"))
                .andExpect(jsonPath("$.data[0].imageUrl").value("https://cdn.example.com/tattoo.jpg"))
                .andExpect(jsonPath("$.data[0].tags[0]").value("traditional"))
                .andExpect(jsonPath("$.pagination.page").value(0))
                .andExpect(jsonPath("$.pagination.size").value(20))
                .andExpect(jsonPath("$.pagination.totalElements").value(1));

        ArgumentCaptor<PageRequest> pageCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(tattooCatalogService).getFeed(isNull(), isNull(), isNull(), pageCaptor.capture());
        assertEquals(0, pageCaptor.getValue().getPageNumber());
        assertEquals(20, pageCaptor.getValue().getPageSize());
    }

    @Test
    void getFeed_passesQueryParamsToService() throws Exception {
        when(tattooCatalogService.getFeed(eq("traditional"), eq("alex"), eq("staff-1"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/public/catalog/tattoos")
                        .param("page", "2")
                        .param("size", "10")
                        .param("tag", "traditional")
                        .param("author", "alex")
                        .param("staffId", "staff-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(0));

        ArgumentCaptor<PageRequest> pageCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(tattooCatalogService).getFeed(eq("traditional"), eq("alex"), eq("staff-1"), pageCaptor.capture());
        assertEquals(2, pageCaptor.getValue().getPageNumber());
        assertEquals(10, pageCaptor.getValue().getPageSize());
    }

    @Test
    void getById_returnsTattoo() throws Exception {
        UUID staffId = UUID.randomUUID();
        when(tattooCatalogService.getById(7L)).thenReturn(sampleTattoo(7L, staffId));

        mockMvc.perform(get("/public/catalog/tattoos/{id}", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.staffId").value(staffId.toString()))
                .andExpect(jsonPath("$.data.status").value("active"))
                .andExpect(jsonPath("$.data.imageUrl").value("https://cdn.example.com/tattoo.jpg"))
                .andExpect(jsonPath("$.data.tags[0]").value("traditional"))
                .andExpect(jsonPath("$.data.showcase").value(true));

        verify(tattooCatalogService).getById(7L);
    }

    @Test
    void search_returnsResults() throws Exception {
        when(tattooCatalogService.search("dragon", 20)).thenReturn(List.of(sampleTattoo(2L, UUID.randomUUID())));

        mockMvc.perform(get("/public/catalog/tattoos/search").param("q", "dragon"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(2))
                .andExpect(jsonPath("$.data[0].tags[0]").value("traditional"));

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(tattooCatalogService).search(queryCaptor.capture(), limitCaptor.capture());
        assertEquals("dragon", queryCaptor.getValue());
        assertEquals(20, limitCaptor.getValue());
    }

    @Test
    void getSimilar_returnsResults() throws Exception {
        when(tattooCatalogService.getSimilar(3L, 12)).thenReturn(List.of(sampleTattoo(4L, UUID.randomUUID())));

        mockMvc.perform(get("/public/catalog/tattoos/{id}/similar", 3L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(4))
                .andExpect(jsonPath("$.data[0].imageUrl").value("https://cdn.example.com/tattoo.jpg"));

        verify(tattooCatalogService).getSimilar(3L, 12);
    }

    @Test
    void getByIds_returnsTattoos() throws Exception {
        when(tattooCatalogService.getByIds(List.of(1L, 2L))).thenReturn(List.of(
                sampleTattoo(1L, UUID.randomUUID()),
                sampleTattoo(2L, UUID.randomUUID())
        ));

        mockMvc.perform(get("/public/catalog/tattoos/by-ids").param("ids", "1", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[1].id").value(2));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(tattooCatalogService).getByIds(idsCaptor.capture());
        assertEquals(List.of(1L, 2L), idsCaptor.getValue());
    }

    @Test
    void getStyles_returnsActiveStyles() throws Exception {
        when(tattooCatalogService.getStyles()).thenReturn(List.of(
                new TattooStyleDto(1L, "traditional", "Traditional", null, List.of())
        ));

        mockMvc.perform(get("/public/catalog/tattoos/styles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].slug").value("traditional"))
                .andExpect(jsonPath("$.data[0].name").value("Traditional"));

        verify(tattooCatalogService).getStyles();
    }

    @Test
    void getAvailableTags_returnsTagSet() throws Exception {
        when(tattooCatalogService.getAvailableTags()).thenReturn(Set.of("traditional", "blackwork"));

        mockMvc.perform(get("/public/catalog/tattoos/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2));

        verify(tattooCatalogService).getAvailableTags();
    }

    private TattooDto sampleTattoo(Long id, UUID staffId) {
        return new TattooDto(
                id,
                staffId,
                "active",
                "https://cdn.example.com/tattoo.jpg",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of("traditional"),
                true
        );
    }
}
