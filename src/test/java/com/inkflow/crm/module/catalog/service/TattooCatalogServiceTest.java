package com.inkflow.crm.module.catalog.service;

import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.module.catalog.dto.CatalogRetagResultDto;
import com.inkflow.crm.module.catalog.dto.CatalogSeedResultDto;
import com.inkflow.crm.module.catalog.dto.TattooDto;
import com.inkflow.crm.module.catalog.dto.TattooStyleDto;
import com.inkflow.crm.module.catalog.entity.Tattoo;
import com.inkflow.crm.module.catalog.entity.TattooStatus;
import com.inkflow.crm.module.catalog.entity.TattooStyle;
import com.inkflow.crm.module.catalog.mapper.TattooMapper;
import com.inkflow.crm.module.catalog.repository.TattooRepository;
import com.inkflow.crm.module.catalog.repository.TattooStyleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TattooCatalogServiceTest {

    @Mock
    private TattooRepository tattooRepository;

    @Mock
    private TattooStyleRepository tattooStyleRepository;

    @Mock
    private UnsplashSeederService seederService;

    @Mock
    private TattooTaggerService taggerService;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private TattooMapper tattooMapper;

    @InjectMocks
    private TattooCatalogService tattooCatalogService;

    @Test
    void getById_rejectsMissingTattoo() {
        when(tattooRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> tattooCatalogService.getById(99L));
    }

    @Test
    void getStyles_returnsActiveStyles() {
        TattooStyle style = new TattooStyle();
        style.setSlug("traditional");
        style.setName("Traditional");

        when(tattooStyleRepository.findByActiveTrueOrderBySortOrderAsc()).thenReturn(List.of(style));
        when(tattooMapper.toStyleDtoList(List.of(style))).thenReturn(List.of(
                new TattooStyleDto(1L, "traditional", "Traditional", null, List.of())
        ));

        List<TattooStyleDto> styles = tattooCatalogService.getStyles();

        assertEquals(1, styles.size());
        assertEquals("traditional", styles.getFirst().slug());
    }

    @Test
    void seed_returnsAddedAndTotalCounts() {
        when(seederService.seed()).thenReturn(3);
        when(tattooRepository.count()).thenReturn(10L);

        CatalogSeedResultDto result = tattooCatalogService.seed();

        assertEquals(3, result.saved());
        assertEquals(10L, result.total());
        verify(seederService).seed();
    }

    @Test
    void retag_returnsUpdatedCount() {
        when(taggerService.retagAll()).thenReturn(7);

        CatalogRetagResultDto result = tattooCatalogService.retag();

        assertEquals(7, result.retagged());
        verify(taggerService).retagAll();
    }

    @Test
    void getByIds_returnsMappedTattoos() {
        UUID staffId = UUID.randomUUID();
        Tattoo tattoo = new Tattoo();
        tattoo.setId(11L);
        tattoo.setStaffId(staffId);
        tattoo.setStatus(TattooStatus.READY);
        tattoo.setImageUrl("https://cdn.example.com/11.jpg");

        when(tattooRepository.findAllById(List.of(11L))).thenReturn(List.of(tattoo));
        when(tattooMapper.toDtoList(List.of(tattoo))).thenReturn(List.of(
                new TattooDto(11L, staffId, "READY", tattoo.getImageUrl(), null,
                        null, null, null, null, null, null, null, null, List.of(), false)
        ));

        List<TattooDto> result = tattooCatalogService.getByIds(List.of(11L));

        assertEquals(1, result.size());
        assertEquals(11L, result.getFirst().id());
    }

    @Test
    void getAvailableTags_delegatesToTagger() {
        when(taggerService.getAvailableTags()).thenReturn(Set.of("traditional", "blackwork"));

        Set<String> tags = tattooCatalogService.getAvailableTags();

        assertEquals(Set.of("traditional", "blackwork"), tags);
    }
}
