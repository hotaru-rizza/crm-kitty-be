package com.inkflow.crm.module.catalog.service;

import com.inkflow.crm.module.catalog.entity.Tattoo;
import com.inkflow.crm.module.catalog.entity.TattooStyle;
import com.inkflow.crm.module.catalog.repository.TattooRepository;
import com.inkflow.crm.module.catalog.repository.TattooStyleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TattooTaggerServiceTest {

    @Mock
    private TattooStyleRepository tattooStyleRepository;

    @Mock
    private TattooRepository tattooRepository;

    @InjectMocks
    private TattooTaggerService taggerService;

    @BeforeEach
    void setUp() {
        TattooStyle traditional = style("traditional", "traditional", "old school");
        when(tattooStyleRepository.findByActiveTrueOrderBySortOrderAsc()).thenReturn(List.of(traditional));
        taggerService.reloadKeywords();
    }

    @Test
    void tagFromText_matchesKeywordSlug() {
        String[] tags = taggerService.tagFromText("Beautiful traditional rose tattoo");

        assertArrayEquals(new String[]{"traditional"}, tags);
    }

    @Test
    void tagFromText_ignoresBlankInputs() {
        String[] tags = taggerService.tagFromText(null, "  ", "traditional design");

        assertArrayEquals(new String[]{"traditional"}, tags);
    }

    @Test
    void shouldReturnEmptyTagsWhenNoKeywordMatches() {
        String[] tags = taggerService.tagFromText("abstract geometric waves");

        assertArrayEquals(new String[]{}, tags);
    }

    @Test
    void shouldReturnMultipleTagsWhenMultipleStylesMatch() {
        TattooStyle realism = style("realism", "realistic", "photo");
        when(tattooStyleRepository.findByActiveTrueOrderBySortOrderAsc())
                .thenReturn(List.of(style("traditional", "traditional"), realism));
        taggerService.reloadKeywords();

        String[] tags = taggerService.tagFromText("traditional realistic portrait");

        assertEquals(2, tags.length);
        assertTrue(java.util.Arrays.asList(tags).contains("traditional"));
        assertTrue(java.util.Arrays.asList(tags).contains("realism"));
    }

    @Test
    void shouldExposeAvailableTagsAfterReload() {
        assertTrue(taggerService.getAvailableTags().contains("traditional"));
    }

    @Test
    void retagAll_updatesEveryTattoo() {
        Tattoo tattoo = new Tattoo();
        tattoo.setDescription("traditional sleeve");
        when(tattooRepository.findAll()).thenReturn(List.of(tattoo));

        int updated = taggerService.retagAll();

        assertEquals(1, updated);
        assertArrayEquals(new String[]{"traditional"}, tattoo.getTags());
        verify(tattooRepository).save(tattoo);
    }

    private TattooStyle style(String slug, String... keywords) {
        TattooStyle style = new TattooStyle();
        style.setSlug(slug);
        style.setKeywords(keywords);
        return style;
    }
}
