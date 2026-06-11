package com.inkflow.crm.module.catalog.service;

import com.inkflow.crm.integration.gemini.GeminiVisionClient;
import com.inkflow.crm.module.catalog.dto.TattooAnalysisDto;
import com.inkflow.crm.module.catalog.entity.Tattoo;
import com.inkflow.crm.module.catalog.entity.TattooStatus;
import com.inkflow.crm.module.catalog.repository.TattooRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioProcessorTest {

    @Mock
    private TattooRepository tattooRepository;

    @Mock
    private VisionService visionService;

    @Mock
    private TattooTaggerService taggerService;

    @Mock
    private EmbeddingService embeddingService;

    @InjectMocks
    private PortfolioProcessor portfolioProcessor;

    @Test
    void processImages_enrichesTattooAndMarksReady() {
        Tattoo tattoo = new Tattoo();
        tattoo.setId(42L);
        tattoo.setImageUrl("https://cdn.example.com/t.jpg");
        tattoo.setStaffId(UUID.randomUUID());

        when(tattooRepository.findById(42L)).thenReturn(Optional.of(tattoo));
        when(visionService.analyze("https://cdn.example.com/t.jpg")).thenReturn(
                new TattooAnalysisDto("desc", "alt", List.of("traditional"))
        );
        when(embeddingService.embedPassage(any())).thenReturn(new float[]{0.1f, 0.2f});

        portfolioProcessor.processImages(List.of(42L));

        assertEquals(TattooStatus.READY, tattoo.getStatus());
        assertEquals("desc", tattoo.getDescription());
        assertArrayEquals(new String[]{"traditional"}, tattoo.getTags());
        verify(tattooRepository).save(tattoo);
    }

    @Test
    void processImages_fallsBackToKeywordTagsWhenVisionReturnsNoTags() {
        Tattoo tattoo = new Tattoo();
        tattoo.setId(43L);
        tattoo.setImageUrl("https://cdn.example.com/t2.jpg");

        when(tattooRepository.findById(43L)).thenReturn(Optional.of(tattoo));
        when(visionService.analyze(any())).thenReturn(new TattooAnalysisDto("traditional rose", "rose", List.of()));
        when(taggerService.tagFromText("traditional rose", "rose")).thenReturn(new String[]{"traditional"});
        when(embeddingService.embedPassage(contains("traditional rose"))).thenReturn(new float[]{0.5f});

        portfolioProcessor.processImages(List.of(43L));

        assertArrayEquals(new String[]{"traditional"}, tattoo.getTags());
        assertEquals(TattooStatus.READY, tattoo.getStatus());
    }

    @Test
    void processImages_marksFailedWhenVisionThrows() {
        Tattoo tattoo = new Tattoo();
        tattoo.setId(44L);
        tattoo.setImageUrl("https://cdn.example.com/broken.jpg");

        when(tattooRepository.findById(44L)).thenReturn(Optional.of(tattoo));
        when(visionService.analyze(any())).thenThrow(new RuntimeException("vision down"));

        portfolioProcessor.processImages(List.of(44L));

        assertEquals(TattooStatus.FAILED, tattoo.getStatus());
        verify(tattooRepository).save(tattoo);
    }
}
